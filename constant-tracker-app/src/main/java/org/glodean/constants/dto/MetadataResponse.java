package org.glodean.constants.dto;

import java.util.List;

/** Combined metadata payload used by filter UIs. */
public record MetadataResponse(
    List<MetadataOptionResponse> types,
    List<MetadataOptionResponse> structuralTypes,
    List<MetadataOptionResponse> semanticTypes) {}
