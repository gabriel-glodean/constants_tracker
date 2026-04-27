package org.glodean.constants.store.postgres.repository;

import org.glodean.constants.store.postgres.entity.ConstantUsageEntity;
import org.glodean.constants.store.postgres.entity.UnitConstantEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * R2DBC reactive repository for {@link ConstantUsageEntity}.
 *
 * <p>Provides a custom finder for loading all usage rows that belong to a single
 * constant value entry, used when reconstructing the full constant model.
 */
public interface ConstantUsageRepository
    extends ReactiveCrudRepository<ConstantUsageEntity, Long> {

  /**
   * Finds all usage entities associated with the given constant.
   *
   * @param constantId the primary key of the owning {@link UnitConstantEntity}
   * @return a {@link Flux} emitting every {@link ConstantUsageEntity} for that constant
   */
  Flux<ConstantUsageEntity> findAllByConstantId(Long constantId);
}
