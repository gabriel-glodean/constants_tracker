package org.glodean.constants.auth;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link AuthCleanupScheduler}.
 *
 * <p>Verifies that each scheduled method delegates to the correct service and that
 * errors from the service (e.g. transient DB failure) are absorbed rather than
 * propagated — a cleanup failure must never crash the scheduler thread.
 */
@ExtendWith(MockitoExtension.class)
class AuthCleanupSchedulerTest {

  @Mock AuthTokenBlacklistService blacklistService;
  @Mock AuthRefreshTokenService refreshTokenService;

  private AuthCleanupScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new AuthCleanupScheduler(blacklistService, refreshTokenService);
  }

  // -------------------------------------------------------------------------
  // purgeBlacklist()
  // -------------------------------------------------------------------------

  @Test
  void purgeBlacklist_delegatesToBlacklistService() {
    when(blacklistService.purgeExpired()).thenReturn(Mono.empty());

    scheduler.purgeBlacklist();

    verify(blacklistService).purgeExpired();
  }

  @Test
  void purgeBlacklist_serviceError_doesNotThrow() {
    when(blacklistService.purgeExpired())
        .thenReturn(Mono.error(new RuntimeException("DB unavailable")));

    assertThatNoException().isThrownBy(() -> scheduler.purgeBlacklist());

    verify(blacklistService).purgeExpired();
  }

  // -------------------------------------------------------------------------
  // purgeRefreshTokens()
  // -------------------------------------------------------------------------

  @Test
  void purgeRefreshTokens_delegatesToRefreshTokenService() {
    when(refreshTokenService.purgeExpired()).thenReturn(Mono.empty());

    scheduler.purgeRefreshTokens();

    verify(refreshTokenService).purgeExpired();
  }

  @Test
  void purgeRefreshTokens_serviceError_doesNotThrow() {
    when(refreshTokenService.purgeExpired())
        .thenReturn(Mono.error(new RuntimeException("DB unavailable")));

    assertThatNoException().isThrownBy(() -> scheduler.purgeRefreshTokens());

    verify(refreshTokenService).purgeExpired();
  }
}
