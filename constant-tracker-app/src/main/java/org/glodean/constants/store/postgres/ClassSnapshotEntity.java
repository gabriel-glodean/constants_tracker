package org.glodean.constants.store.postgres;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** R2DBC entity representing a versioned class snapshot in the {@code class_snapshots} table. */
@Table("class_snapshots")
public record ClassSnapshotEntity(@Id Long id, String project, String className, int version) {}

