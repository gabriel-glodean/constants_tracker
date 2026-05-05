package org.glodean.constants.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.dto.TokenResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Handles JWT access-token generation, validation, and revocation.
 *
 * <p>Token structure (HMAC-SHA256 signed):
 * <ul>
 *   <li>{@code sub} — username</li>
 *   <li>{@code uid} — database user id (avoids an extra lookup at logout/blacklist time)</li>
 *   <li>{@code jti} — UUID used to reference the token in the blacklist</li>
 *   <li>{@code iat} / {@code exp} — issued-at / expiry</li>
 * </ul>
 *
 * <p>{@link #isValid(String)} is reactive ({@code Mono<Boolean>}) because it must consult the
 * {@link AuthTokenBlacklistService} blacklist, which performs a DB round-trip.
 */
@Service
@ConditionalOnProperty(name = "constants.auth.enabled", havingValue = "true", matchIfMissing = true)
public class JwtService {

  private static final Logger log = LogManager.getLogger(JwtService.class);
  private static final String CLAIM_UID = "uid";

  private final AuthProperties authProperties;
  private final AuthUserService userService;
  private final AuthTokenBlacklistService blacklistService;
  private final AuthRefreshTokenService refreshTokenService;
  private final PasswordEncoder passwordEncoder;
  private final SecretKey signingKey;

  public JwtService(
      AuthProperties authProperties,
      AuthUserService userService,
      AuthTokenBlacklistService blacklistService,
      AuthRefreshTokenService refreshTokenService,
      PasswordEncoder passwordEncoder) {
    this.authProperties = authProperties;
    this.userService = userService;
    this.blacklistService = blacklistService;
    this.refreshTokenService = refreshTokenService;
    this.passwordEncoder = passwordEncoder;
    this.signingKey = Keys.hmacShaKeyFor(
        authProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Generate a signed JWT for the given subject.
   *
   * @param subject the principal identifier (username)
   * @param userId  the database primary key, embedded as the {@code uid} claim
   * @return a compact, URL-safe JWT string
   */
  public String generateToken(String subject, Long userId) {
    long nowMs = System.currentTimeMillis();
    long expMs = nowMs + authProperties.jwt().expirationMs();
    return Jwts.builder()
        .subject(subject)
        .claim(CLAIM_UID, userId)
        .id(UUID.randomUUID().toString())
        .issuedAt(new Date(nowMs))
        .expiration(new Date(expMs))
        .signWith(signingKey)
        .compact();
  }

  /**
   * Validate the given JWT string, including a blacklist check.
   *
   * @param token the JWT to validate
   * @return {@code true} if the token has a valid signature, is not expired, and is not blacklisted
   */
  public Mono<Boolean> isValid(String token) {
    Claims claims;
    try {
      claims = parseClaims(token);
    } catch (JwtException | IllegalArgumentException e) {
      log.atDebug().log("JWT parse/validation failed: {}", e.getMessage());
      return Mono.just(false);
    }
    String jti = claims.getId();
    if (jti == null) return Mono.just(false);
    return blacklistService.isBlacklisted(jti)
        .map(blacklisted -> !blacklisted)
        .doOnNext(valid -> log.atDebug().log("JWT jti={} valid={}", jti, valid));
  }

  /**
   * Extract the subject claim from a (trusted, already-validated) token.
   *
   * @param token the JWT string
   * @return the subject claim value (username)
   */
  public String extractSubject(String token) {
    return parseClaims(token).getSubject();
  }

  /**
   * Validate credentials and, if correct, generate an access token and a refresh token.
   *
   * <p>The BCrypt hash is converted from {@code char[]} to {@code String} only for the duration
   * of the {@link PasswordEncoder#matches} call; the array is zeroed immediately afterwards.
   *
   * @param username the principal identifier
   * @param password the plain-text credential
   * @return a {@link TokenResponse} with the signed JWT, a refresh token, and the access-token
   *         lifetime in ms
   * @throws BadCredentialsException if the user is unknown or the password does not match
   */
  public Mono<TokenResponse> authenticate(String username, String password) {
    return userService.findByUsername(username)
        .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid credentials")))
        .flatMap(user -> {
          // Materialize hash as String only for the BCrypt check, then zero the char[].
          String hash = new String(user.passwordHash());
          Arrays.fill(user.passwordHash(), '\0');
          if (!passwordEncoder.matches(password, hash)) {
            log.atWarn().log("Bad credentials for user: {}", username);
            return Mono.error(new BadCredentialsException("Invalid credentials"));
          }
          String accessToken = generateToken(username, user.id());
          Duration ttl = Duration.ofMillis(authProperties.refreshToken().ttlMs());
          return refreshTokenService.issue(user.id(), ttl)
              .flatMap(refreshToken -> userService.updateLastLogin(user.id())
                  .thenReturn(new TokenResponse(
                      accessToken, refreshToken, authProperties.jwt().expirationMs())));
        })
        .doOnNext(_ -> log.atInfo().log("Authenticated user: {}", username));
  }

  /**
   * Generate a fresh access token and rotate the refresh token for an already-authenticated
   * subject (legacy {@code /auth/token/renew} path).
   *
   * <p>All previously active refresh tokens for the user are revoked and a new one is issued,
   * ensuring the session cannot be silently extended via the old refresh token after renewal.
   *
   * @param subject the principal identifier (from the existing, validated token)
   * @return a {@link TokenResponse} with the new JWT and a freshly rotated refresh token
   */
  public Mono<TokenResponse> renewToken(String subject) {
    return userService.findByUsername(subject)
        .switchIfEmpty(Mono.error(new BadCredentialsException("User not found")))
        .flatMap(user -> {
          String newAccessToken = generateToken(subject, user.id());
          Duration ttl = Duration.ofMillis(authProperties.refreshToken().ttlMs());
          // issue() revokes all previous active refresh tokens before saving the new one
          return refreshTokenService.issue(user.id(), ttl)
              .map(newRefreshToken -> new TokenResponse(
                  newAccessToken, newRefreshToken, authProperties.jwt().expirationMs()));
        });
  }

  /**
   * Exchange a valid refresh token for a new access token + rotated refresh token.
   *
   * <p>Rotation strategy: the old refresh token is revoked (via
   * {@link AuthRefreshTokenService#issue} which calls {@code revokeAllActiveForUser} first) and a
   * brand-new one is issued. The caller must store the new refresh token and discard the old one.
   *
   * @param rawRefreshToken the raw refresh token received from the client
   * @return a {@link TokenResponse} with a fresh access token and a new refresh token
   * @throws BadCredentialsException if the refresh token is invalid, revoked, or expired
   */
  public Mono<TokenResponse> refresh(String rawRefreshToken) {
    return refreshTokenService.validate(rawRefreshToken)
        .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid or expired refresh token")))
        .flatMap(tokenEntity -> userService.findById(tokenEntity.userId())
            .switchIfEmpty(Mono.error(new BadCredentialsException("User not found")))
            .flatMap(user -> {
              String newAccessToken = generateToken(user.username(), user.id());
              Duration ttl = Duration.ofMillis(authProperties.refreshToken().ttlMs());
              // issue() revokes all previous active tokens before saving the new one (rotation)
              return refreshTokenService.issue(user.id(), ttl)
                  .map(newRefreshToken -> new TokenResponse(
                      newAccessToken, newRefreshToken, authProperties.jwt().expirationMs()));
            }))
        .doOnNext(_ -> log.atInfo().log("Refresh token rotated"));
  }

  /**
   * Invalidate a session: blacklist the access token <em>and</em> revoke the associated refresh
   * token (if one is provided). Both operations are run concurrently via {@link Mono#when} so that
   * a failure in either is reported and neither is silently skipped.
   *
   * @param rawAccessToken  the JWT extracted from the {@code Authorization} header
   * @param rawRefreshToken the refresh token to revoke; may be {@code null} if the caller did not
   *                        supply one
   * @return a {@link Mono} that completes when both operations finish
   */
  public Mono<Void> logout(String rawAccessToken, String rawRefreshToken) {
    Mono<Void> blacklistAccess = invalidate(rawAccessToken);
    Mono<Void> revokeRefresh = rawRefreshToken != null
        ? refreshTokenService.validate(rawRefreshToken)
            .flatMap(entity -> refreshTokenService.revoke(entity.id()))
        : Mono.empty();
    return Mono.when(blacklistAccess, revokeRefresh);
  }

  /**
   * Revoke an access token before its natural expiry by recording its {@code jti} in the
   * blacklist table. The JTI and expiry are parsed from the token itself, so no additional
   * DB lookup is needed.
   *
   * @param token the raw JWT to invalidate (typically taken from the {@code Authorization} header
   *              at logout time)
   * @return a {@link Mono} that completes when the token is recorded in the blacklist
   */
  public Mono<Void> invalidate(String token) {
    Claims claims;
    try {
      claims = parseClaims(token);
    } catch (JwtException | IllegalArgumentException e) {
      log.atDebug().log("Ignoring invalidation for unparseable token: {}", e.getMessage());
      return Mono.empty();
    }
    String jti = claims.getId();
    Long userId = claims.get(CLAIM_UID, Long.class);
    if (jti == null || userId == null) {
      log.atWarn().log("Token is missing jti or uid claim — skipping blacklist");
      return Mono.empty();
    }
    OffsetDateTime expiresAt = claims.getExpiration().toInstant().atOffset(ZoneOffset.UTC);
    log.atDebug().log("Blacklisting token jti={} for userId={}", jti, userId);
    return blacklistService.blacklist(jti, userId, expiresAt);
  }

  // -------------------------------------------------------------------------
  // Internal
  // -------------------------------------------------------------------------

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
