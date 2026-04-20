package org.glodean.constants.store.postgres;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC reactive repository for {@link UnitSnapshotEntity}.
 */
public interface UnitSnapshotRepository
    extends ReactiveCrudRepository<UnitSnapshotEntity, Long> {

  /**
   * Finds all snapshots belonging to a given descriptor.
   */
  Flux<UnitSnapshotEntity> findAllByDescriptorId(Long descriptorId);

  /**
   * Finds a snapshot by descriptor id and unit name.
   */
  Mono<UnitSnapshotEntity> findByDescriptorIdAndUnitName(Long descriptorId, String unitName);
}

