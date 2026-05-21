package org.glodean.constants.store.postgres.repository;

import org.glodean.constants.store.postgres.entity.JarExtractionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * R2DBC reactive repository for {@link JarExtractionEntity}.
 */
public interface JarExtractionRepository
    extends ReactiveCrudRepository<JarExtractionEntity, Long> {

  /**
   * Finds the extraction job for the given project, version, and fat JAR name.
   */
  Mono<JarExtractionEntity> findByProjectAndVersionAndJarName(
      String project, int version, String jarName);

}
