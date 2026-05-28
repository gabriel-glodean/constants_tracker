package org.glodean.constants.store.postgres.repository;

import java.time.OffsetDateTime;

import org.glodean.constants.store.postgres.entity.JarExtractionEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
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

  /**
   * Atomically increments {@code nested_processed} on the most recent row for this job.
   * Returns the number of rows updated (always 0 or 1).
   */
  @Modifying
  @Query("""
      UPDATE fat_jar_extractions
         SET nested_processed = nested_processed + 1,
             last_updated_at  = :now
       WHERE id = (
           SELECT id FROM fat_jar_extractions
            WHERE project  = :project
              AND version  = :version
              AND jar_name = :jarName
            ORDER BY id DESC
            LIMIT 1
       )
      """)
  Mono<Integer> incrementNestedProcessed(String project, int version, String jarName, OffsetDateTime now);

  /**
   * Atomically increments {@code nested_failed} on the most recent row for this job.
   * Returns the number of rows updated (always 0 or 1).
   */
  @Modifying
  @Query("""
      UPDATE fat_jar_extractions
         SET nested_failed   = nested_failed + 1,
             last_updated_at = :now
       WHERE id = (
           SELECT id FROM fat_jar_extractions
            WHERE project  = :project
              AND version  = :version
              AND jar_name = :jarName
            ORDER BY id DESC
            LIMIT 1
       )
      """)
  Mono<Integer> incrementNestedFailed(String project, int version, String jarName, OffsetDateTime now);

}
