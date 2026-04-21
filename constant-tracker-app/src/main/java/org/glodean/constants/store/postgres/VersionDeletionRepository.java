package org.glodean.constants.store.postgres;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VersionDeletionRepository
    extends ReactiveCrudRepository<VersionDeletionEntity, Long> {

  Mono<VersionDeletionEntity> findByProjectAndVersionAndUnitPath(
      String project, int version, String unitPath);

  Mono<Boolean> existsByProjectAndVersionAndUnitPath(
      String project, int version, String unitPath);

  /** All deletion records for a given project+version (used by diff resolution). */
  Flux<VersionDeletionEntity> findAllByProjectAndVersion(String project, int version);
}