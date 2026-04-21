package org.glodean.constants.dto;

import java.util.List;

/**
 * A single constant value whose presence or usages changed between two versions.
 *
 * <ul>
 *   <li>{@code fromUsages} empty → constant was <b>added</b> in the newer version.</li>
 *   <li>{@code toUsages} empty → constant was <b>removed</b> in the newer version.</li>
 *   <li>Both non-empty but different → constant still present but usages <b>changed</b>.</li>
 * </ul>
 */
public record ConstantDiffEntry(
    String value,
    String valueType,
    List<UsageDetail> fromUsages,
    List<UsageDetail> toUsages) {

  /** Convenience classifier derived from the two usage lists. */
  public enum ChangeKind {
    ADDED,
    REMOVED,
    CHANGED
  }

  public ChangeKind changeKind() {
    if (fromUsages.isEmpty()) return ChangeKind.ADDED;
    if (toUsages.isEmpty()) return ChangeKind.REMOVED;
    return ChangeKind.CHANGED;
  }
}

