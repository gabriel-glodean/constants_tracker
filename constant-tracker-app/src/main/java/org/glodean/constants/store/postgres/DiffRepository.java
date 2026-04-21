package org.glodean.constants.store.postgres;

import java.util.Collection;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Raw SQL repository for the diff endpoint.
 *
 * <p>Loads all constant + usage rows for a given set of snapshot IDs in a single
 * batched 2-table query, avoiding N+1 round trips. Path resolution is done in-memory
 * by the caller using the already-resolved {@code snapshotId → path} map.
 */
@Repository
public class DiffRepository {

  private final DatabaseClient db;

  public DiffRepository(DatabaseClient db) {
    this.db = db;
  }

  private static final String SQL =
      """
      SELECT uc.snapshot_id,
             uc.constant_value,
             uc.constant_value_type,
             cu.structural_type,
             cu.semantic_type_kind,
             cu.semantic_type_name,
             cu.semantic_display_name,
             cu.semantic_description,
             cu.location_class_name,
             cu.location_method_name,
             cu.location_method_descriptor,
             cu.location_bytecode_offset,
             cu.location_line_number,
             cu.confidence
      FROM unit_constants uc
      JOIN constant_usages cu ON cu.constant_id = uc.id
      WHERE uc.snapshot_id = ANY(:snapshotIds)
      ORDER BY uc.snapshot_id, uc.constant_value
      """;

  /**
   * Returns all constant + usage rows for the provided snapshot IDs.
   * Returns an empty {@link Flux} immediately when the collection is empty.
   */
  public Flux<ConstantDiffRow> loadForSnapshots(Collection<Long> snapshotIds) {
    if (snapshotIds.isEmpty()) {
      return Flux.empty();
    }
    return db.sql(SQL)
        .bind("snapshotIds", snapshotIds.toArray(Long[]::new))
        .map(
            row ->
                new ConstantDiffRow(
                    row.get("snapshot_id", Long.class),
                    row.get("constant_value", String.class),
                    row.get("constant_value_type", String.class),
                    row.get("structural_type", String.class),
                    row.get("semantic_type_kind", String.class),
                    row.get("semantic_type_name", String.class),
                    row.get("semantic_display_name", String.class),
                    row.get("semantic_description", String.class),
                    row.get("location_class_name", String.class),
                    row.get("location_method_name", String.class),
                    row.get("location_method_descriptor", String.class),
                    row.get("location_bytecode_offset", Integer.class),
                    row.get("location_line_number", Integer.class),
                    row.get("confidence", Double.class)))
        .all();
  }
}

