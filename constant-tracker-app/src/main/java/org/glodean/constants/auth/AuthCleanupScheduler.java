package org.glodean.constants.auth;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled housekeeping for auth tables.
 *
 * <p>Runs two nightly cleanup jobs:
 * <ul>
 *   <li>Purge expired rows from {@code auth_token_blacklist} — Redis entries expire
 *       automatically via TTL, but PostgreSQL rows need explicit deletion.</li>
 *   <li>Purge expired rows from {@code auth_refresh_tokens} — both revoked and
 *       naturally-expired tokens whose TTL has passed.</li>
 * </ul>
 *
 * <p>Both cron expressions are configurable via {@code application.yaml}:
 * <pre>
 * constants.auth.cleanup.blacklist-cron        (default: 02:00 UTC daily)
 * constants.auth.cleanup.refresh-tokens-cron   (default: 02:15 UTC daily)
 * </pre>
 *
 * <p>Only active when {@code constants.auth.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "constants.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthCleanupScheduler {

  private static final Logger log = LogManager.getLogger(AuthCleanupScheduler.class);

  private final AuthTokenBlacklistService blacklistService;
  private final AuthRefreshTokenService refreshTokenService;

  @Autowired
  public AuthCleanupScheduler(
      AuthTokenBlacklistService blacklistService,
      AuthRefreshTokenService refreshTokenService) {
    this.blacklistService = blacklistService;
    this.refreshTokenService = refreshTokenService;
  }

  /**
   * Purges expired rows from {@code auth_token_blacklist}.
   * Redis entries expire automatically; only the PostgreSQL audit trail needs sweeping.
   */
  @Scheduled(cron = "${constants.auth.cleanup.blacklist-cron:0 0 2 * * *}")
  public void purgeBlacklist() {
    log.atInfo().log("Scheduled: purging expired token blacklist entries");
    blacklistService
        .purgeExpired()
        .doOnSuccess(_ -> log.atInfo().log("Token blacklist purge completed"))
        .doOnError(ex -> log.atError().withThrowable(ex).log("Token blacklist purge failed"))
        .subscribe();
  }

  /**
   * Purges expired rows from {@code auth_refresh_tokens} (both revoked and naturally expired).
   */
  @Scheduled(cron = "${constants.auth.cleanup.refresh-tokens-cron:0 15 2 * * *}")
  public void purgeRefreshTokens() {
    log.atInfo().log("Scheduled: purging expired refresh tokens");
    refreshTokenService
        .purgeExpired()
        .doOnSuccess(_ -> log.atInfo().log("Refresh token purge completed"))
        .doOnError(ex -> log.atError().withThrowable(ex).log("Refresh token purge failed"))
        .subscribe();
  }
}
