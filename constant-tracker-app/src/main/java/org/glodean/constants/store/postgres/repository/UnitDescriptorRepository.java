package org.glodean.constants.store.postgres.repository;

import org.glodean.constants.store.postgres.entity.UnitDescriptorEntity;
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

  /**
   * Checks whether a descriptor with the given content hash already exists for the
   * project/version combination. Used by nested JAR extraction to skip re-extraction
   * of library JARs that were already indexed in a previous upload of the same fat JAR.
   */
  Mono<UnitDescriptorEntity> findByProjectAndVersionAndContentHash(
      String project, int version, String contentHash);
}
