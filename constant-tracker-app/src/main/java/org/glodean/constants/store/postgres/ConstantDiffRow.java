package org.glodean.constants.store.postgres;

/**
 * Flat projection of a joined unit_constants + constant_usages row, used by {@link DiffRepository}.
 *
 * <p>Uses {@code snapshotId} instead of {@code path} so the query only touches 2 tables.
 * The caller maps {@code snapshotId → path} using the already-resolved effective snapshot map.
 *
 * <p>One row is produced per usage observation; a single constant value may produce multiple rows.
 */
public record ConstantDiffRow(
    long snapshotId,
    String constantValue,
    String constantValueType,
    String structuralType,
    String semanticTypeKind,
    String semanticTypeName,
    String semanticDisplayName,
    String semanticDescription,
    String locationClassName,
    String locationMethodName,
    String locationMethodDescriptor,
    Integer locationBytecodeOffset,
    Integer locationLineNumber,
    double confidence) {}

