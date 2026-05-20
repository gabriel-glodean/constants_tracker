package org.glodean.constants.store.postgres.repository;

import org.glodean.constants.store.postgres.entity.UnitSnapshotEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC reactive repository for {@link UnitSnapshotEntity}.
 */
public interface UnitSnapshotRepository
    extends ReactiveCrudRepository<UnitSnapshotEntity, Long> {

  /** Finds a snapshot by descriptor id and unit name. */
  Mono<UnitSnapshotEntity> findByDescriptorIdAndUnitName(Long descriptorId, String unitName);

  /**
   * Inserts or updates a snapshot. On conflict on {@code (descriptor_id, unit_name)} the JSON
   * is overwritten and the existing row is returned. Using this instead of {@code save()} avoids
   * {@code DuplicateKeyException} when a JAR contains duplicate ZIP entries.
   */
  @Query("""
      INSERT INTO unit_snapshots (descriptor_id, unit_name, unit_constants_json)
      VALUES (:descriptorId, :unitName, :unitConstantsJson)
      ON CONFLICT (descriptor_id, unit_name)
      DO UPDATE SET unit_constants_json = EXCLUDED.unit_constants_json
      RETURNING id, descriptor_id, unit_name, unit_constants_json
      """)
  Mono<UnitSnapshotEntity> upsert(Long descriptorId, String unitName, String unitConstantsJson);

  /**
   * Deletes all snapshots for a descriptor in one statement.
   * Cascades to {@code unit_constants} → {@code constant_usages} via DB-level FK cascades.
   */
  Mono<Void> deleteAllByDescriptorId(Long descriptorId);

  /**
   * Finds all snapshots for a project/version by joining with {@code unit_descriptors}.
   * Used by {@link org.glodean.constants.services.ProjectVersionService} to enumerate
   * class-file-level paths for removal tracking (replaces the old descriptor-path enumeration).
   */
  @Query("SELECT s.* FROM unit_snapshots s " +
      "JOIN unit_descriptors d ON s.descriptor_id = d.id " +
      "WHERE d.project = :project AND d.version = :version")
  Flux<UnitSnapshotEntity> findAllByProjectAndVersion(String project, int version);

  /**
   * Finds a single snapshot by its class-file path ({@code unit_name}) within a project/version.
   * Used by the {@code find()} query in
   * {@link org.glodean.constants.store.postgres.PostgresService}.
   */
  @Query("SELECT s.* FROM unit_snapshots s " +
      "JOIN unit_descriptors d ON s.descriptor_id = d.id " +
      "WHERE d.project = :project AND d.version = :version AND s.unit_name = :unitName " +
      "LIMIT 1")
  Mono<UnitSnapshotEntity> findByProjectAndVersionAndUnitName(
      String project, int version, String unitName);
}
