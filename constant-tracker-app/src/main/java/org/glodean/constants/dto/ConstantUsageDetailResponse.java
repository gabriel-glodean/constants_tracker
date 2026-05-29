package org.glodean.constants.dto;
/**
 * Flat constant+usage row returned by {@code GET /units/constants}.
 *
 * <p>One entry is emitted per {@code (constant_value, constant_usage)} pair a constant
 * with multiple usages therefore appears multiple times with different location/type fields.
 * This keeps the response simple and pagination predictable.
 */
public record ConstantUsageDetailResponse(
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
