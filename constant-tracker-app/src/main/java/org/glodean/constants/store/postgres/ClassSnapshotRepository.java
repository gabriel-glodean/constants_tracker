package org.glodean.constants.store.postgres;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * R2DBC reactive repository for {@link ClassSnapshotEntity}.
 *
 * <p>Provides a derived query for looking up the exact versioned snapshot of a class,
 * which is the primary read path used by {@link PostgresService#find(String)}.
 */
public interface ClassSnapshotRepository
    extends ReactiveCrudRepository<ClassSnapshotEntity, Long> {

  /**
   * Finds the snapshot that matches the given project/class/version triple.
   *
   * @param project   the project identifier (e.g., {@code "jdk"})
   * @param className the slash-separated internal class name (e.g., {@code "java/lang/String"})
   * @param version   the version number
   * @return a {@link Mono} emitting the matching snapshot, or empty if not found
   */
  Mono<ClassSnapshotEntity> findByProjectAndClassNameAndVersion(
      String project, String className, int version);
}
