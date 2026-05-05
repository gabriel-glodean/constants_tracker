package org.glodean.constants.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.store.postgres.entity.AuthRefreshTokenEntity;
import org.glodean.constants.store.postgres.repository.AuthRefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Manages the lifecycle of refresh tokens stored in {@code auth_refresh_tokens}.
 *
 * <p>Rotation strategy: whenever a refresh token is used or the user logs out, all active tokens
 * for that user are revoked before a new one is issued. Only the SHA-256 hash of the raw token
 * is persisted; the raw value is returned to the caller once and never stored.
 */
@Service
public class AuthRefreshTokenService {

  private static final Logger log = LogManager.getLogger(AuthRefreshTokenService.class);

  private final AuthRefreshTokenRepository refreshTokenRepository;

  @Autowired
  public AuthRefreshTokenService(AuthRefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  /**
   * Issues a new refresh token for the given user, revoking all previous active tokens first
   * (rotation). The raw token string is returned — it must be sent to the client and will not
   * be recoverable from storage.
   *
   * @param userId the user's primary key
   * @param ttl    how long the token should be valid
   * @return the raw (un-hashed) refresh token string
   */
  @Transactional
  public Mono<String> issue(Long userId, Duration ttl) {
    String rawToken = UUID.randomUUID().toString();
    String tokenHash = sha256Hex(rawToken);
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime expiresAt = now.plus(ttl);

    return revokeAllActiveForUser(userId)
        .then(Mono.defer(() -> {
          var entity = new AuthRefreshTokenEntity(null, userId, tokenHash, now, expiresAt, false, null);
          return refreshTokenRepository.save(entity);
        }))
        .doOnNext(saved -> log.atDebug().log(
            "Issued refresh token id={} for userId={} expires={}", saved.id(), userId, expiresAt))
        .thenReturn(rawToken);
  }

  /**
   * Validates a raw refresh token. Returns the stored entity only if the token exists,
   * is not revoked, and has not expired.
   *
   * @param rawToken the raw refresh token received from the client
   * @return the matching {@link AuthRefreshTokenEntity}, or empty if invalid/expired
   */
  public Mono<AuthRefreshTokenEntity> validate(String rawToken) {
    String tokenHash = sha256Hex(rawToken);
    return refreshTokenRepository
        .findByTokenHash(tokenHash)
        .filter(t -> !t.revoked())
        .filter(t -> t.expiresAt().isAfter(OffsetDateTime.now()))
        .doOnNext(t -> log.atDebug().log("Refresh token id={} is valid", t.id()))
        .switchIfEmpty(Mono.defer(() -> {
          log.atDebug().log("Refresh token is invalid, revoked, or expired");
          return Mono.empty();
        }));
  }

  /**
   * Revokes a single refresh token by its database id.
   *
   * @param id the token's primary key
   * @return a {@link Mono} that completes when the token is marked revoked
   */
  public Mono<Void> revoke(Long id) {
    return refreshTokenRepository.findById(id)
        .flatMap(token -> {
          var revoked = new AuthRefreshTokenEntity(
              token.id(), token.userId(), token.tokenHash(),
              token.issuedAt(), token.expiresAt(), true, OffsetDateTime.now());
          return refreshTokenRepository.save(revoked);
        })
        .doOnNext(t -> log.atDebug().log("Revoked refresh token id={}", t.id()))
        .then();
  }

  /**
   * Revokes all active (non-revoked) refresh tokens for the given user.
   * Called during logout and as part of token rotation.
   *
   * @param userId the user's primary key
   * @return a {@link Mono} that completes when all active tokens are revoked
   */
  public Mono<Void> revokeAllActiveForUser(Long userId) {
    OffsetDateTime now = OffsetDateTime.now();
    return refreshTokenRepository
        .findByUserIdAndRevokedFalse(userId)
        .flatMap(token -> {
          var revoked = new AuthRefreshTokenEntity(
              token.id(), token.userId(), token.tokenHash(),
              token.issuedAt(), token.expiresAt(), true, now);
          return refreshTokenRepository.save(revoked);
        })
        .doOnNext(t -> log.atDebug().log("Revoked refresh token id={} for userId={}", t.id(), userId))
        .then();
  }

  /**
   * Deletes all expired refresh tokens (both revoked and unrevoked whose TTL has passed).
   * Safe to invoke periodically from a scheduled task.
   *
   * @return a {@link Mono} that completes when cleanup is done
   */
  public Mono<Void> purgeExpired() {
    OffsetDateTime threshold = OffsetDateTime.now();
    log.atInfo().log("Purging expired refresh tokens before {}", threshold);
    return refreshTokenRepository.deleteByExpiresAtBefore(threshold);
  }

  // -------------------------------------------------------------------------
  // Utilities
  // -------------------------------------------------------------------------

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed by the JVM specification
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
