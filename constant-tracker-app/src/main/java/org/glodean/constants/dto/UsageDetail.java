package org.glodean.constants.dto;

/** Flattened usage observation returned in the diff response. */
public record UsageDetail(
    String structuralType,
    String semanticTypeKind,
    String semanticTypeName,
    String semanticDisplayName,
    String locationClassName,
    String locationMethodName,
    Integer locationLineNumber,
    double confidence) {}
