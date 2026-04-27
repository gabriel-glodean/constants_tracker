package org.glodean.constants.store.postgres.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity for the {@code project_versions} table.
 *
 * <p>Tracks version lifecycle (OPEN → FINALIZED) and inheritance chain via {@code parentVersion}.
 */
@Table("project_versions")
public record ProjectVersionEntity(
    @Id Long id,
    String project,
    int version,
    Integer parentVersion,
    String status,
    LocalDateTime createdAt,
    LocalDateTime finalizedAt) {

  public static final String STATUS_OPEN = "OPEN";
  public static final String STATUS_FINALIZED = "FINALIZED";
}
