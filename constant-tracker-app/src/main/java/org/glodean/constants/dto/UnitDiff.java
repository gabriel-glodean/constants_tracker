package org.glodean.constants.dto;

import java.util.List;

/**
 * Diff result for a single unit (class file, config file, etc.) between two project versions.
 *
 * <p>Units with no changes are never included in the response.
 *
 * @param path             unit path (e.g. {@code org/example/Foo.class})
 * @param addedUnit        {@code true} when the unit exists only in the newer version
 * @param removedUnit      {@code true} when the unit exists only in the older version
 * @param changedConstants constant-level diff (added / removed / changed entries)
 */
public record UnitDiff(
    String path,
    boolean addedUnit,
    boolean removedUnit,
    List<ConstantDiffEntry> changedConstants) {}
