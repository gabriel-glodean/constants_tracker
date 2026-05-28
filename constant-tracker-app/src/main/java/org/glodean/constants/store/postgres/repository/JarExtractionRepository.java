package org.glodean.constants.store.postgres.repository;

import org.glodean.constants.store.postgres.entity.JarExtractionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC reactive repository for {@link JarExtractionEntity}.
 */
public interface JarExtractionRepository
    extends ReactiveCrudRepository<JarExtractionEntity, Long> {

  /**
   * Finds the most recent extraction job for the given project, version, and fat JAR name.
   *
   * <p>Multiple rows can exist for the same (project, version, jarName) when the same JAR is
   * uploaded more than once. This method always returns the latest one by {@code id} so that
   * counter updates and UI polling operate on the current run, not a previous completed job.
   */
  Mono<JarExtractionEntity> findFirstByProjectAndVersionAndJarNameOrderByIdDesc(
      String project, int version, String jarName);

  /** Finds all extraction jobs for the given project/version. */
  Flux<JarExtractionEntity> findAllByProjectAndVersion(String project, int version);

}
