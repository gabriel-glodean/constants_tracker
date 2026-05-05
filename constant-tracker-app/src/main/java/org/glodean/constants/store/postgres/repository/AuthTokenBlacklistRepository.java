package org.glodean.constants.store.postgres.repository;

import java.time.OffsetDateTime;
import org.glodean.constants.store.postgres.entity.AuthTokenBlacklistEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for {@code auth_token_blacklist}.
 *
 * <p>The primary key is the JWT {@code jti} claim (a UUID string), so {@link #existsById}
 * is the fast path for checking whether a given access token has been revoked.
 * Rows whose {@code expiresAt} has passed can be safely purged via
 * {@link #deleteByExpiresAtBefore}.
 */
public interface AuthTokenBlacklistRepository
    extends ReactiveCrudRepository<AuthTokenBlacklistEntity, String> {

  /**
   * Removes all blacklist entries whose tokens have already expired naturally.
   * Safe to run periodically (e.g. via a scheduled task) to keep the table small.
   *
   * @param threshold cut-off timestamp; entries expiring before this are removed
   * @return a {@link Mono} that completes when the cleanup is done
   */
  Mono<Void> deleteByExpiresAtBefore(OffsetDateTime threshold);
}
