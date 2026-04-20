package org.glodean.constants.dto;

import java.util.List;

/**
 * A single search hit returned by a fuzzy constant search.
 *
 * <p>Represents one unit snapshot that contains at least one constant value matching the
 * query. The {@code constantValues} list contains the actual constants found in that snapshot,
 * <em>not</em> a pre-filtered subset — let the caller highlight or rank them further.
 *
 * @param project        the project this unit belongs to
 * @param unitName       unit name / path (e.g. {@code org/example/MyService})
 * @param version        the stored version number of this snapshot
 * @param sourceKind     the kind of source (e.g. {@code "CLASS_FILE"}, {@code "JAR"})
 * @param constantValues all constant values indexed for this snapshot
 * @param semanticPairs  semantic type annotations in the form {@code "value|TYPE|confidence"}
 */
public record FuzzySearchHit(String project, String unitName, int version, String sourceKind, List<String> constantValues, List<String> semanticPairs) {}

