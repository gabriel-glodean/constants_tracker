package org.glodean.constants.store.postgres;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

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
}

