package org.glodean.constants.store.postgres.repository;

import java.util.Collection;
import org.glodean.constants.store.postgres.entity.UnitConstantEntity;
import org.glodean.constants.store.postgres.entity.UnitSnapshotEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC reactive repository for {@link UnitConstantEntity}.
 */
public interface UnitConstantRepository
    extends ReactiveCrudRepository<UnitConstantEntity, Long> {

  /**
   * Finds all constant entities associated with the given snapshot.
   *
   * @param snapshotId the primary key of the owning {@link UnitSnapshotEntity}
   * @return a {@link Flux} emitting every {@link UnitConstantEntity} for that snapshot
   */
  Flux<UnitConstantEntity> findAllBySnapshotId(Long snapshotId);

  /**
   * Finds all constant entities associated with any of the given snapshot IDs.
   * Used for bulk operations when processing a whole batch at once.
   */
  Flux<UnitConstantEntity> findAllBySnapshotIdIn(Collection<Long> snapshotIds);

  /**
   * Deletes all constant entities associated with the given snapshot in a single
   * {@code DELETE FROM unit_constants WHERE snapshot_id = ?} statement.
   *
   * @param snapshotId the primary key of the owning {@link UnitSnapshotEntity}
   */
  Mono<Void> deleteAllBySnapshotId(Long snapshotId);

  /**
   * Bulk-deletes all constant entities for the given snapshot IDs in a single SQL statement.
   * Used when replacing normalized rows for an entire batch to avoid per-file round-trips.
   */
  @Query("DELETE FROM unit_constants WHERE snapshot_id IN (:snapshotIds)")
  Mono<Void> deleteAllBySnapshotIdIn(Collection<Long> snapshotIds);
}
