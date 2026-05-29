package org.glodean.constants.dto;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import org.glodean.constants.web.validation.ValidProjectName;
import org.springframework.validation.annotation.Validated;
/**
 * Query-parameter record shared by {@code GET /units} and {@code GET /units/constants}.
 *
 * <p>Bound via {@code @ModelAttribute} so Spring maps each query-string key to its
 * corresponding record component.  Optional integer components use {@link Integer} (boxed)
 * so that absent parameters arrive as {@code null} rather than causing a type-conversion
 * failure; callers resolve defaults with {@link #effectivePage()} and
 * {@link #effectivePageSize(int)}.
 *
 * <p>Constraints are placed on record components and enforced by the {@link Validated}
 * controller.
 *
 * <p>{@code semanticType} accepts the type name as exposed by {@code /metadata/semantic-types}
 * (e.g. {@code LOG_MESSAGE} for core types, {@code my-custom} for custom ones).
 * The DB-level {@code semantic_type_kind} column value is inferred at query time via
 * {@link org.glodean.constants.store.SemanticTypeStore} — callers do not need to supply it.
 */
public record UnitListingRequest(
    @NotBlank
    @ValidProjectName
    String project,
    @Positive
    int version,
    // Optional — structural_type column value, e.g. METHOD_INVOCATION_PARAMETER (matches /metadata/usage-types)
    String structuralType,
    // Optional — semantic type name as returned by /metadata/semantic-types, e.g. LOG_MESSAGE
    String semanticType,
    // Optional — constant_value_type column value, e.g. String, Integer, Long
    String constantValueType,
    // Zero-based page index; null is treated as 0
    @PositiveOrZero
    Integer page,
    // Page size; null is treated as the per-endpoint default
    @Positive
    Integer pageSize) {
  /** Returns {@code true} when at least one usage filter is set. */
  public boolean isFiltered() {
    return structuralType != null || semanticType != null || constantValueType != null;
  }
  /** Returns {@link #page()} or {@code 0} when absent. */
  public int effectivePage() {
    return page != null ? page : 0;
  }
  /**
   * Returns {@link #pageSize()} or {@code defaultPageSize} when absent.
   *
   * @param defaultPageSize per-endpoint default (e.g. 50 for listing, 100 for detail)
   */
  public int effectivePageSize(int defaultPageSize) {
    return pageSize != null ? pageSize : defaultPageSize;
  }
  /** Convenience: zero-based row offset for the current page. */
  public long offset(int defaultPageSize) {
    return (long) effectivePage() * effectivePageSize(defaultPageSize);
  }
}
