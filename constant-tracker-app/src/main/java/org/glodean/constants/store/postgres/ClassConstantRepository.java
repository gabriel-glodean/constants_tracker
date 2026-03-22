package org.glodean.constants.store.postgres;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * R2DBC reactive repository for {@link ClassConstantEntity}.
 *
 * <p>Extends Spring Data's {@link ReactiveCrudRepository} with a custom finder
 * that retrieves all constant rows belonging to a single class snapshot.
 */
public interface ClassConstantRepository
    extends ReactiveCrudRepository<ClassConstantEntity, Long> {

  /**
   * Finds all constant entities associated with the given snapshot.
   *
   * @param snapshotId the primary key of the owning {@link ClassSnapshotEntity}
   * @return a {@link Flux} emitting every {@link ClassConstantEntity} for that snapshot
   */
  Flux<ClassConstantEntity> findAllBySnapshotId(Long snapshotId);
}
