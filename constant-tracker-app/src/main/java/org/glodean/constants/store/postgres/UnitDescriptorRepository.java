package org.glodean.constants.store.postgres;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC reactive repository for {@link UnitDescriptorEntity}.
 */
public interface UnitDescriptorRepository
    extends ReactiveCrudRepository<UnitDescriptorEntity, Long> {

  /**
   * Finds the descriptor that matches the given project/path/version triple.
   */
  Mono<UnitDescriptorEntity> findByProjectAndPathAndVersion(
      String project, String path, int version);

  /**
   * Finds all descriptors for a given project and version (useful for diff).
   */
  Flux<UnitDescriptorEntity> findAllByProjectAndVersion(String project, int version);
}

