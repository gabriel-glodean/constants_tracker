package org.glodean.constants.dto;

import java.util.List;

/** Top-level response for a project version diff. */
public record ProjectDiffResponse(
    String project,
    int fromVersion,
    int toVersion,
    List<UnitDiff> units) {}
