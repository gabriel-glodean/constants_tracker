package org.glodean.constants.store.postgres.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity representing a versioned resource descriptor in the {@code unit_descriptors} table.
 *
 * <p>Captures the identity of a source artifact (JAR, class file, directory, etc.)
 * within a project at a given version, enabling version-to-version diff and removal detection.
 */
@Table("unit_descriptors")
public record UnitDescriptorEntity(
    @Id Long id,
    String project,
    int version,
    String sourceKind,
    String path,
    long sizeBytes,
    String contentHash) {}
