package org.glodean.constants.dto;
/**
 * Flat constant+usage row returned by {@code GET /units/constants}.
 *
 * <p>One entry is emitted per distinct {@code (constantValue, metadata)} group. Duplicates are
 * collapsed by the SQL {@code GROUP BY}, so a constant that appears N times in the same structural
 * context produces exactly one entry with {@code occurrenceCount = N}.
 *
 * <p>{@code metadata} carries the structural context of the usage as a JSON object.
 * For {@code METHOD_INVOCATION_PARAMETER} the keys are {@code calleeOwner},
 * {@code calleeName}, {@code calleeDescriptor}, and {@code receiverKind}, giving the
 * exact method that receives the constant as an argument.  For {@code FIELD_STORE} the
 * keys are {@code fieldOwner}, {@code fieldName}, {@code fieldDescriptor}, and
 * {@code receiverKind}.  Other structural types may have an empty object ({@code {}}).
 *
 * <p>{@code occurrenceCount} is the number of raw usage rows sharing the same
 * {@code (constantValue, metadata)} within the result set (before paging).
 * Use it to prioritise high-frequency unknown constant+context patterns.
 */
public record ConstantUsageDetailResponse(
    String constantValue,
    String constantValueType,
    String structuralType,
    String semanticType,
    Double confidence,
    String metadata,
    Long occurrenceCount) {}
