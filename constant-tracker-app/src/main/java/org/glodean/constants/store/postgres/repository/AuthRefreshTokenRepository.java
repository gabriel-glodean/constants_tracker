package org.glodean.constants.store.postgres.repository;

import java.time.OffsetDateTime;
import org.glodean.constants.store.postgres.entity.AuthRefreshTokenEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for {@code auth_refresh_tokens}.
 *
 * <p>Supports token issuance (via {@link #save}), validation ({@link #findByTokenHash}),
 * rotation ({@link #findByUserIdAndRevokedFalse}), and housekeeping
 * ({@link #deleteByExpiresAtBefore}).
 */
public interface AuthRefreshTokenRepository
    extends ReactiveCrudRepository<AuthRefreshTokenEntity, Long> {

  /**
   * Finds a token record by its SHA-256 hash.
   *
   * @param tokenHash hex-encoded SHA-256 of the raw refresh token
   * @return the matching entity, or empty if not found
   */
  Mono<AuthRefreshTokenEntity> findByTokenHash(String tokenHash);

  /**
   * Returns all non-revoked tokens for the given user (used during rotation/logout).
   *
   * @param userId the user's primary key
   * @return a {@link Flux} of active token entities
   */
  Flux<AuthRefreshTokenEntity> findByUserIdAndRevokedFalse(Long userId);

  /**
   * Deletes all tokens whose expiry is strictly before {@code threshold}.
   * Intended for periodic cleanup jobs.
   *
   * @param threshold cut-off timestamp; tokens expiring before this are removed
   * @return a {@link Mono} that completes when all matching rows are deleted
   */
  Mono<Void> deleteByExpiresAtBefore(OffsetDateTime threshold);
}
