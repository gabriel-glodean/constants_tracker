package org.glodean.constants.store.postgres.repository.projection;

/**
 * Flat projection row carrying a single constant value together with one of its
 * usage observations.  Used by the {@code GET /units/constants} endpoint so that
 * the LLM tool can inspect unclassified (or any-typed) constants in bulk without
 * fetching full snapshot JSON.
 *
 * <p>One {@code ConstantDetailRow} is emitted per distinct {@code (constantValue, metadata)} group.
 * Duplicates are collapsed by a SQL {@code GROUP BY}, so a constant that appears N times in the
 * same structural context produces exactly one row with {@code occurrenceCount = N}.
 *
 * <p>{@code metadata} contains the structural context of the usage as a JSON object —
 * for {@code METHOD_INVOCATION_PARAMETER} this is {@code calleeOwner}, {@code calleeName},
 * {@code calleeDescriptor}, and {@code receiverKind}; for {@code FIELD_STORE} it is
 * {@code fieldOwner}, {@code fieldName}, {@code fieldDescriptor}, and {@code receiverKind}.
 *
 * <p>{@code occurrenceCount} is the number of raw usage rows that share the same
 * {@code (constantValue, metadata)} within the current project/version (and any active filters),
 * computed via {@code COUNT(*)} in the {@code GROUP BY} query.
 */
public record ConstantDetailRow(
    String constantValue,
    String constantValueType,
    String structuralType,
    String semanticType,
    Double confidence,
    String metadata,
    Long occurrenceCount) {}
