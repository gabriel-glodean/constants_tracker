package org.glodean.constants.store.postgres;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity representing a unit snapshot in the {@code unit_snapshots} table.
 *
 * <p>Each snapshot belongs to a {@link UnitDescriptorEntity} and holds the
 * extracted constants as a JSON blob.
 */
@Table("unit_snapshots")
public record UnitSnapshotEntity(
    @Id Long id,
    Long descriptorId,
    String unitName,
    String unitConstantsJson) {}

