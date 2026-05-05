package org.glodean.constants.auth;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.store.postgres.entity.AuthTokenBlacklistEntity;
import org.glodean.constants.store.postgres.repository.AuthTokenBlacklistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Manages the JWT access-token blacklist.
 *
 * <p>Two-tier storage:
 * <ol>
 *   <li><b>Redis</b> — primary fast path; each entry is stored under
 *       {@code blacklist:jti:<jti>} with a TTL equal to the token's remaining lifetime, so Redis
 *       auto-expires the key when the token would have expired anyway.</li>
 *   <li><b>PostgreSQL</b> ({@code auth_token_blacklist}) — durable audit trail. Also serves as
 *       fallback if Redis is restarted and an entry is missing from cache.</li>
 * </ol>
 *
 * <p>{@link #isBlacklisted(String)} checks Redis first; on a cache miss it consults PostgreSQL,
 * ensuring correctness even after a Redis flush/restart.
 */
@Service
public record AuthTokenBlacklistService(
    @Autowired AuthTokenBlacklistRepository blacklistRepository,
    @Autowired ReactiveStringRedisTemplate redis) {

  private static final Logger log = LogManager.getLogger(AuthTokenBlacklistService.class);
  private static final String KEY_PREFIX = "blacklist:jti:";

  /**
   * Records a revoked access token in both Redis (with TTL) and PostgreSQL (durable).
   *
   * @param jti       the {@code jti} claim from the access JWT
   * @param userId    the user that owned the token
   * @param expiresAt the token's original expiry (mirrors the JWT {@code exp} claim)
   * @return a {@link Mono} that completes when both writes are done
   */
  public Mono<Void> blacklist(String jti, Long userId, OffsetDateTime expiresAt) {
    OffsetDateTime now = OffsetDateTime.now();
    Duration ttl = Duration.between(now, expiresAt);

    // Write to Redis (best-effort — a failure here is logged but does not abort the DB write).
    Mono<Void> rediWrite = (ttl.isPositive()
        ? redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl)
        : redis.opsForValue().set(KEY_PREFIX + jti, "1"))
        .doOnNext(_ -> log.atDebug().log(
            "Cached blacklist entry jti={} in Redis ttl={}s", jti, ttl.toSeconds()))
        .onErrorResume(ex -> {
          log.atWarn().withThrowable(ex).log("Redis write failed for jti={} — continuing", jti);
          return Mono.just(Boolean.FALSE);
        })
        .then();

    var entity = new AuthTokenBlacklistEntity(jti, userId, expiresAt, now);
    Mono<Void> dbWrite = blacklistRepository.save(entity)
        .doOnNext(_ -> log.atDebug().log(
            "Persisted blacklist entry jti={} for userId={}", jti, userId))
        .then();

    return Mono.when(rediWrite, dbWrite);
  }

  /**
   * Returns {@code true} if the given JTI has been explicitly revoked.
   *
   * <p>Checks Redis first (fast path). On a cache miss, falls back to PostgreSQL so that tokens
   * revoked before a Redis restart are still correctly rejected.
   *
   * @param jti the {@code jti} claim to check
   * @return {@code true} if the token is blacklisted and still within its original lifetime
   */
  public Mono<Boolean> isBlacklisted(String jti) {
    return redis.hasKey(KEY_PREFIX + jti)
        .flatMap(inRedis -> {
          if (Boolean.TRUE.equals(inRedis)) {
            log.atDebug().log("Blacklist hit (Redis) jti={}", jti);
            return Mono.just(true);
          }
          // Cache miss — check PostgreSQL as fallback
          return blacklistRepository.findById(jti)
              .map(entry -> {
                boolean active = entry.expiresAt().isAfter(OffsetDateTime.now());
                if (active) {
                  log.atDebug().log("Blacklist hit (DB fallback) jti={} — re-caching in Redis", jti);
                  // Re-populate Redis so future checks are fast again
                  Duration remaining = Duration.between(OffsetDateTime.now(), entry.expiresAt());
                  if (remaining.isPositive()) {
                    redis.opsForValue().set(KEY_PREFIX + jti, "1", remaining).subscribe();
                  }
                }
                return active;
              })
              .defaultIfEmpty(false);
        })
        .onErrorResume(ex -> {
          log.atWarn().withThrowable(ex).log(
              "Redis check failed for jti={} — falling back to DB", jti);
          return blacklistRepository.findById(jti)
              .map(entry -> entry.expiresAt().isAfter(OffsetDateTime.now()))
              .defaultIfEmpty(false);
        });
  }

  /**
   * Deletes PostgreSQL blacklist entries whose tokens have already expired naturally.
   * Redis entries expire automatically via TTL; only the DB needs periodic cleanup.
   *
   * @return a {@link Mono} that completes when the cleanup is done
   */
  public Mono<Void> purgeExpired() {
    OffsetDateTime threshold = OffsetDateTime.now();
    log.atInfo().log("Purging expired DB blacklist entries before {}", threshold);
    return blacklistRepository.deleteByExpiresAtBefore(threshold);
  }
}
