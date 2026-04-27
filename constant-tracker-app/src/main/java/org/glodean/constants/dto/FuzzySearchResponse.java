package org.glodean.constants.dto;

import java.util.List;

/**
 * Aggregated response for a fuzzy constant search.
 *
 * @param hits       ranked list of matching class snapshots
 * @param totalFound total number of documents matched in Solr (may exceed {@code hits.size()} when
 *                   pagination is in effect)
 */
public record FuzzySearchResponse(List<FuzzySearchHit> hits, long totalFound) {}
