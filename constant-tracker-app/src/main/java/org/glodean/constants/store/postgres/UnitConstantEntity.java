package org.glodean.constants.store.postgres;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** R2DBC entity for a single constant value belonging to a unit snapshot. */
@Table("unit_constants")
public record UnitConstantEntity(
    @Id Long id, Long snapshotId, String constantValue, String constantValueType) {}

