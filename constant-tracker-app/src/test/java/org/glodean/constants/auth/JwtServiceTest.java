package org.glodean.constants.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.glodean.constants.dto.TokenResponse;
import org.glodean.constants.store.postgres.entity.AuthRefreshTokenEntity;
import org.glodean.constants.store.postgres.entity.AuthUserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link JwtService}.
 *
 * <p>All collaborators are mocked; only the synchronous JWT building/parsing
 * path (via JJWT) is exercised against a real in-memory secret key.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

  private static final String SECRET =
      "test-secret-key-for-unit-testing-purposes-32chars!!"; // ≥ 32 bytes for HS256
  private static final long EXPIRATION_MS = 60_000L; // 1 minute
  private static final long REFRESH_TTL_MS = 7 * 24 * 3600 * 1000L; // 7 days

  @Mock AuthUserService userService;
  @Mock AuthTokenBlacklistService blacklistService;
  @Mock AuthRefreshTokenService refreshTokenService;

  private PasswordEncoder passwordEncoder;
  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder();
    var authProperties = new AuthProperties(
        true,
        new AuthProperties.Jwt(SECRET, EXPIRATION_MS),
        new AuthProperties.RefreshToken(REFRESH_TTL_MS));
    jwtService =
        new JwtService(authProperties, userService, blacklistService, refreshTokenService, passwordEncoder);
  }

  // -------------------------------------------------------------------------
  // generateToken / extractSubject
  // -------------------------------------------------------------------------

  @Test
  void generateToken_returnsCompactJwt() {
    String token = jwtService.generateToken("alice", 42L);
    assertThat(token).isNotBlank().contains(".");
  }

  @Test
  void extractSubject_returnsExpectedUsername() {
    String token = jwtService.generateToken("alice", 42L);
    assertThat(jwtService.extractSubject(token)).isEqualTo("alice");
  }

  // -------------------------------------------------------------------------
  // isValid
  // -------------------------------------------------------------------------

  @Test
  void isValid_validNotBlacklisted_returnsTrue() {
    String token = jwtService.generateToken("alice", 1L);
    when(blacklistService.isBlacklisted(anyString())).thenReturn(Mono.just(false));

    StepVerifier.create(jwtService.isValid(token))
        .expectNext(true)
        .verifyComplete();
  }

  @Test
  void isValid_blacklistedToken_returnsFalse() {
    String token = jwtService.generateToken("alice", 1L);
    when(blacklistService.isBlacklisted(anyString())).thenReturn(Mono.just(true));

    StepVerifier.create(jwtService.isValid(token))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  void isValid_expiredToken_returnsFalse() {
    // Build an already-expired token manually
    SecretKey key =
        Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    String expired =
        Jwts.builder()
            .subject("alice")
            .id(UUID.randomUUID().toString())
            .issuedAt(new Date(System.currentTimeMillis() - 120_000))
            .expiration(new Date(System.currentTimeMillis() - 60_000))
            .signWith(key)
            .compact();

    StepVerifier.create(jwtService.isValid(expired))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  void isValid_malformedToken_returnsFalse() {
    StepVerifier.create(jwtService.isValid("not.a.jwt"))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  void isValid_tokenWithNoJti_returnsFalse() {
    // Build token without jti claim
    SecretKey key =
        Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    long nowMs = System.currentTimeMillis();
    String noJti =
        Jwts.builder()
            .subject("alice")
            .issuedAt(new Date(nowMs))
            .expiration(new Date(nowMs + EXPIRATION_MS))
            .signWith(key)
            .compact();

    StepVerifier.create(jwtService.isValid(noJti))
        .expectNext(false)
        .verifyComplete();
  }

  // -------------------------------------------------------------------------
  // authenticate
  // -------------------------------------------------------------------------

  @Test
  void authenticate_validCredentials_returnsTokenResponse() {
    String rawPassword = "correct-horse";
    String hash = passwordEncoder.encode(rawPassword);
    var user = new AuthUserEntity(7L, "alice", hash.toCharArray(), true, null, null);

    when(userService.findByUsername("alice")).thenReturn(Mono.just(user));
    when(refreshTokenService.issue(eq(7L), any(Duration.class)))
        .thenReturn(Mono.just("raw-refresh-token-abc"));
    when(userService.updateLastLogin(7L)).thenReturn(Mono.empty());

    StepVerifier.create(jwtService.authenticate("alice", rawPassword))
        .assertNext(
            resp -> {
              assertThat(resp).isInstanceOf(TokenResponse.class);
              assertThat(resp.accessToken()).isNotBlank();
              assertThat(resp.refreshToken()).isEqualTo("raw-refresh-token-abc");
              assertThat(resp.expiresInMs()).isEqualTo(EXPIRATION_MS);
            })
        .verifyComplete();
  }

  @Test
  void authenticate_unknownUser_throwsBadCredentials() {
    when(userService.findByUsername("unknown")).thenReturn(Mono.empty());

    StepVerifier.create(jwtService.authenticate("unknown", "any"))
        .expectError(BadCredentialsException.class)
        .verify();
  }

  @Test
  void authenticate_wrongPassword_throwsBadCredentials() {
    String hash = passwordEncoder.encode("real-password");
    var user = new AuthUserEntity(1L, "alice", hash.toCharArray(), true, null, null);

    when(userService.findByUsername("alice")).thenReturn(Mono.just(user));

    StepVerifier.create(jwtService.authenticate("alice", "wrong-password"))
        .expectError(BadCredentialsException.class)
        .verify();
  }

  // -------------------------------------------------------------------------
  // renewToken
  // -------------------------------------------------------------------------

  @Test
  void renewToken_existingUser_returnsNewTokenWithRotatedRefreshToken() {
    var user = new AuthUserEntity(3L, "bob", new char[0], true, null, null);
    when(userService.findByUsername("bob")).thenReturn(Mono.just(user));
    when(refreshTokenService.issue(eq(3L), any(Duration.class)))
        .thenReturn(Mono.just("new-refresh-token-bob"));

    StepVerifier.create(jwtService.renewToken("bob"))
        .assertNext(resp -> {
          assertThat(resp.accessToken()).isNotBlank();
          assertThat(jwtService.extractSubject(resp.accessToken())).isEqualTo("bob");
          // renewToken now rotates the refresh token — must not be null
          assertThat(resp.refreshToken()).isEqualTo("new-refresh-token-bob");
          assertThat(resp.expiresInMs()).isEqualTo(EXPIRATION_MS);
        })
        .verifyComplete();

    verify(refreshTokenService).issue(eq(3L), any(Duration.class));
  }

  @Test
  void renewToken_unknownUser_throwsBadCredentials() {
    when(userService.findByUsername("ghost")).thenReturn(Mono.empty());

    StepVerifier.create(jwtService.renewToken("ghost"))
        .expectError(BadCredentialsException.class)
        .verify();
  }

  // -------------------------------------------------------------------------
  // refresh
  // -------------------------------------------------------------------------

  @Test
  void refresh_validRefreshToken_returnsNewTokenPair() {
    var tokenEntity = new AuthRefreshTokenEntity(
        42L, 7L, "hash", OffsetDateTime.now(), OffsetDateTime.now().plusDays(7), false, null);
    var user = new AuthUserEntity(7L, "alice", new char[0], true, null, null);

    when(refreshTokenService.validate("raw-rt")).thenReturn(Mono.just(tokenEntity));
    when(userService.findById(7L)).thenReturn(Mono.just(user));
    when(refreshTokenService.issue(eq(7L), any(Duration.class))).thenReturn(Mono.just("new-raw-rt"));

    StepVerifier.create(jwtService.refresh("raw-rt"))
        .assertNext(resp -> {
          assertThat(resp.accessToken()).isNotBlank();
          assertThat(jwtService.extractSubject(resp.accessToken())).isEqualTo("alice");
          assertThat(resp.refreshToken()).isEqualTo("new-raw-rt");
          assertThat(resp.expiresInMs()).isEqualTo(EXPIRATION_MS);
        })
        .verifyComplete();
  }

  @Test
  void refresh_invalidRefreshToken_throwsBadCredentials() {
    when(refreshTokenService.validate("bad-rt")).thenReturn(Mono.empty());

    StepVerifier.create(jwtService.refresh("bad-rt"))
        .expectError(BadCredentialsException.class)
        .verify();
  }

  // -------------------------------------------------------------------------
  // logout
  // -------------------------------------------------------------------------

  @Test
  void logout_blacklistsAccessTokenAndRevokesRefreshToken() {
    String accessToken = jwtService.generateToken("alice", 5L);
    var tokenEntity = new AuthRefreshTokenEntity(
        10L, 5L, "hash", OffsetDateTime.now(), OffsetDateTime.now().plusDays(7), false, null);

    when(blacklistService.blacklist(anyString(), eq(5L), any())).thenReturn(Mono.empty());
    when(refreshTokenService.validate("raw-rt")).thenReturn(Mono.just(tokenEntity));
    when(refreshTokenService.revoke(10L)).thenReturn(Mono.empty());

    StepVerifier.create(jwtService.logout(accessToken, "raw-rt"))
        .verifyComplete();

    verify(blacklistService).blacklist(anyString(), eq(5L), any());
    verify(refreshTokenService).revoke(10L);
  }

  @Test
  void logout_nullRefreshToken_onlyBlacklistsAccessToken() {
    String accessToken = jwtService.generateToken("alice", 5L);
    when(blacklistService.blacklist(anyString(), eq(5L), any())).thenReturn(Mono.empty());

    StepVerifier.create(jwtService.logout(accessToken, null))
        .verifyComplete();

    verify(blacklistService).blacklist(anyString(), eq(5L), any());
    verifyNoInteractions(refreshTokenService);
  }

  @Test
  void logout_refreshTokenRevocationFails_errorIsPropagated() {
    // With Mono.when(), a failure in either branch propagates to the caller.
    String accessToken = jwtService.generateToken("alice", 5L);
    var tokenEntity = new AuthRefreshTokenEntity(
        10L, 5L, "hash", OffsetDateTime.now(), OffsetDateTime.now().plusDays(7), false, null);

    when(blacklistService.blacklist(anyString(), eq(5L), any())).thenReturn(Mono.empty());
    when(refreshTokenService.validate("raw-rt")).thenReturn(Mono.just(tokenEntity));
    when(refreshTokenService.revoke(10L))
        .thenReturn(Mono.error(new RuntimeException("DB failure")));

    StepVerifier.create(jwtService.logout(accessToken, "raw-rt"))
        .expectError(RuntimeException.class)
        .verify();

    // The access token blacklist was still attempted (both run in parallel)
    verify(blacklistService).blacklist(anyString(), eq(5L), any());
  }

  @Test
  void logout_blacklistFails_errorIsPropagated() {
    // With Mono.when(), a failure in the blacklist branch also propagates.
    String accessToken = jwtService.generateToken("alice", 5L);
    var tokenEntity = new AuthRefreshTokenEntity(
        10L, 5L, "hash", OffsetDateTime.now(), OffsetDateTime.now().plusDays(7), false, null);

    when(blacklistService.blacklist(anyString(), eq(5L), any()))
        .thenReturn(Mono.error(new RuntimeException("Redis down")));
    when(refreshTokenService.validate("raw-rt")).thenReturn(Mono.just(tokenEntity));
    when(refreshTokenService.revoke(10L)).thenReturn(Mono.empty());

    StepVerifier.create(jwtService.logout(accessToken, "raw-rt"))
        .expectError(RuntimeException.class)
        .verify();
  }

  // -------------------------------------------------------------------------
  // invalidate
  // -------------------------------------------------------------------------

  @Test
  void invalidate_validToken_blacklistsIt() {
    String token = jwtService.generateToken("alice", 5L);
    when(blacklistService.blacklist(anyString(), eq(5L), any())).thenReturn(Mono.empty());

    StepVerifier.create(jwtService.invalidate(token))
        .verifyComplete();

    verify(blacklistService).blacklist(anyString(), eq(5L), any());
  }

  @Test
  void invalidate_malformedToken_completesWithoutError() {
    StepVerifier.create(jwtService.invalidate("bad.token.value"))
        .verifyComplete();

    verifyNoInteractions(blacklistService);
  }
}
