package org.glodean.constants.store.postgres.repository.projection;

/**
 * Flat projection row carrying a single constant value together with one of its
 * usage observations.  Used by the {@code GET /units/constants} endpoint so that
 * the LLM tool can inspect unclassified (or any-typed) constants in bulk without
 * fetching full snapshot JSON.
 *
 * <p>One {@code ConstantDetailRow} is emitted per {@code (unit_constant, constant_usage)} pair,
 * so a constant with multiple usages produces multiple rows.
 */
public record ConstantDetailRow(
    String constantValue,
    String constantValueType,
    String structuralType,
    String semanticTypeKind,
    String semanticTypeName,
    String semanticDisplayName,
    String locationClassName,
    String locationMethodName,
    String locationMethodDescriptor,
    Integer locationLineNumber,
    Double confidence) {}
