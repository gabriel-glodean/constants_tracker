package org.glodean.constants.auth;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.glodean.constants.store.postgres.entity.AuthRefreshTokenEntity;
import org.glodean.constants.store.postgres.repository.AuthRefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
/** Unit tests for {@link AuthRefreshTokenService}. */
@ExtendWith(MockitoExtension.class)
class AuthRefreshTokenServiceTest {
  @Mock AuthRefreshTokenRepository refreshTokenRepository;
  private AuthRefreshTokenService service;
  @BeforeEach
  void setUp() {
    service = new AuthRefreshTokenService(refreshTokenRepository);
  }
  // -------------------------------------------------------------------------
  // issue()
  // -------------------------------------------------------------------------
  @Test
  void issue_revokesExistingAndSavesNew_returnsRawToken() {
    long userId = 1L;
    var saved = new AuthRefreshTokenEntity(
        10L, userId, "hash", OffsetDateTime.now(),
        OffsetDateTime.now().plusDays(7), false, null);
    when(refreshTokenRepository.findByUserIdAndRevokedFalse(userId)).thenReturn(Flux.empty());
    when(refreshTokenRepository.save(any())).thenReturn(Mono.just(saved));
    StepVerifier.create(service.issue(userId, Duration.ofDays(7)))
        .assertNext(rawToken -> {
          assert rawToken != null && !rawToken.isBlank();
        })
        .verifyComplete();
    verify(refreshTokenRepository).save(any());
  }
  @Test
  void issue_revokesExistingActiveTokensFirst() {
    long userId = 2L;
    var active = new AuthRefreshTokenEntity(
        20L, userId, "old-hash", OffsetDateTime.now().minusDays(1),
        OffsetDateTime.now().plusDays(6), false, null);
    var revoked = new AuthRefreshTokenEntity(
        20L, userId, "old-hash", active.issuedAt(),
        active.expiresAt(), true, OffsetDateTime.now());
    var newToken = new AuthRefreshTokenEntity(
        21L, userId, "new-hash", OffsetDateTime.now(),
        OffsetDateTime.now().plusDays(7), false, null);
    when(refreshTokenRepository.findByUserIdAndRevokedFalse(userId)).thenReturn(Flux.just(active));
    when(refreshTokenRepository.save(any()))
        .thenReturn(Mono.just(revoked))  // first call revokes existing
        .thenReturn(Mono.just(newToken)); // second call saves new
    StepVerifier.create(service.issue(userId, Duration.ofDays(7)))
        .assertNext(raw -> org.junit.jupiter.api.Assertions.assertFalse(raw.isEmpty()))
        .verifyComplete();
  }
  // -------------------------------------------------------------------------
  // validate()
  // -------------------------------------------------------------------------
  @Test
  void validate_validToken_returnsEntity() {
    var future = OffsetDateTime.now().plusHours(1);
    var entity = new AuthRefreshTokenEntity(5L, 1L, "hash", OffsetDateTime.now(), future, false, null);
    // Call service.validate("rawToken") — it will SHA-256 it internally
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.just(entity));
    StepVerifier.create(service.validate("any-raw-token"))
        .expectNext(entity)
        .verifyComplete();
  }
  @Test
  void validate_revokedToken_returnsEmpty() {
    var entity = new AuthRefreshTokenEntity(
        5L, 1L, "hash", OffsetDateTime.now(),
        OffsetDateTime.now().plusHours(1), true, OffsetDateTime.now());
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.just(entity));
    StepVerifier.create(service.validate("any-raw-token"))
        .verifyComplete(); // empty
  }
  @Test
  void validate_expiredToken_returnsEmpty() {
    var entity = new AuthRefreshTokenEntity(
        5L, 1L, "hash", OffsetDateTime.now().minusDays(2),
        OffsetDateTime.now().minusDays(1), false, null);
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.just(entity));
    StepVerifier.create(service.validate("any-raw-token"))
        .verifyComplete(); // empty
  }
  @Test
  void validate_unknownToken_returnsEmpty() {
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.empty());
    StepVerifier.create(service.validate("unknown-token"))
        .verifyComplete();
  }
  // -------------------------------------------------------------------------
  // revoke()
  // -------------------------------------------------------------------------
  @Test
  void revoke_marksTokenRevoked() {
    var entity = new AuthRefreshTokenEntity(
        7L, 1L, "hash", OffsetDateTime.now(),
        OffsetDateTime.now().plusHours(1), false, null);
    var revokedEntity = new AuthRefreshTokenEntity(
        7L, 1L, "hash", entity.issuedAt(),
        entity.expiresAt(), true, OffsetDateTime.now());
    when(refreshTokenRepository.findById(7L)).thenReturn(Mono.just(entity));
    when(refreshTokenRepository.save(any())).thenReturn(Mono.just(revokedEntity));
    StepVerifier.create(service.revoke(7L)).verifyComplete();
    verify(refreshTokenRepository).save(argThat(e -> ((AuthRefreshTokenEntity) e).revoked()));
  }
  // -------------------------------------------------------------------------
  // revokeAllActiveForUser()
  // -------------------------------------------------------------------------
  @Test
  void revokeAllActiveForUser_revokesAll() {
    long userId = 3L;
    var t1 = new AuthRefreshTokenEntity(
        30L, userId, "h1", OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), false, null);
    var t2 = new AuthRefreshTokenEntity(
        31L, userId, "h2", OffsetDateTime.now(), OffsetDateTime.now().plusDays(2), false, null);
    when(refreshTokenRepository.findByUserIdAndRevokedFalse(userId)).thenReturn(Flux.just(t1, t2));
    when(refreshTokenRepository.save(any()))
        .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    StepVerifier.create(service.revokeAllActiveForUser(userId)).verifyComplete();
    verify(refreshTokenRepository, times(2)).save(argThat(e -> ((AuthRefreshTokenEntity) e).revoked()));
  }
  @Test
  void revokeAllActiveForUser_noActiveTokens_completesCleanly() {
    when(refreshTokenRepository.findByUserIdAndRevokedFalse(99L)).thenReturn(Flux.empty());
    StepVerifier.create(service.revokeAllActiveForUser(99L)).verifyComplete();
    verify(refreshTokenRepository, never()).save(any());
  }
  // -------------------------------------------------------------------------
  // purgeExpired()
  // -------------------------------------------------------------------------
  @Test
  void purgeExpired_delegatesToRepository() {
    when(refreshTokenRepository.deleteByExpiresAtBefore(any())).thenReturn(Mono.empty());
    StepVerifier.create(service.purgeExpired()).verifyComplete();
    verify(refreshTokenRepository).deleteByExpiresAtBefore(any());
  }
}
