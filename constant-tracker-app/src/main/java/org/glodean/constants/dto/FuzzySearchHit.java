package org.glodean.constants.dto;

import java.util.List;

/**
 * A single search hit returned by a fuzzy constant search.
 *
 * <p>Represents one class snapshot that contains at least one constant value matching the
 * query. The {@code constantValues} list contains the actual constants found in that snapshot,
 * <em>not</em> a pre-filtered subset — let the caller highlight or rank them further.
 *
 * @param project        the project this class belongs to
 * @param className      internal class name (e.g. {@code org/example/MyService})
 * @param version        the stored version number of this snapshot
 * @param constantValues all constant values indexed for this snapshot
 */
public record FuzzySearchHit(String project, String className, int version, List<String> constantValues) {}

