package org.glodean.constants.web.endpoints;

import jakarta.validation.Valid;
import java.util.List;
import org.glodean.constants.dto.ConstantUsageDetailResponse;
import org.glodean.constants.dto.UnitListingRequest;
import org.glodean.constants.store.postgres.UnitConstantQueries;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Read-only endpoint that returns a paged flat list of individual constant values together with
 * their usage observations.
 *
 * <p>Intended for the LLM-based semantic-type discovery tool, which needs to inspect raw constant
 * values and their structural/location context in bulk without fetching full snapshot JSON.
 *
 * <h3>Key design decisions</h3>
 * <ul>
 *   <li>One row per {@code (unit_constant, constant_usage)} pair — a constant with multiple usages
 *       appears multiple times.  The LLM tool groups by value+structuralType as needed.</li>
 *   <li>Paging is always active ({@code page} / {@code pageSize}, default 100 rows).</li>
 *   <li>All filters are optional; omitting all of them returns every constant usage for the
 *       project/version (subject to the page limit).</li>
 *   <li>The typical tool invocation filters with {@code semanticTypeKind=CORE} and
 *       {@code semanticTypeName=UNKNOWN} to target unclassified constants only.</li>
 * </ul>
 *
 * <h3>Example request</h3>
 * <pre>{@code
 * GET /units/constants
 *       ?project=my-service
 *       &version=3
 *       &semanticTypeKind=CORE
 *       &semanticTypeName=UNKNOWN
 *       &page=0
 *       &pageSize=100
 * }</pre>
 */
@Validated
@RestController
@RequestMapping("/units/constants")
public class ConstantDetailController {

  private static final int DEFAULT_PAGE_SIZE = 100;

  private final UnitConstantQueries unitConstantQueries;

  public ConstantDetailController(UnitConstantQueries unitConstantQueries) {
    this.unitConstantQueries = unitConstantQueries;
  }

  @GetMapping
  public Mono<List<ConstantUsageDetailResponse>> listConstantDetails(
      @Valid @ModelAttribute UnitListingRequest req) {

    return unitConstantQueries
        .constantDetails(req, DEFAULT_PAGE_SIZE)
        .map(row -> new ConstantUsageDetailResponse(
            row.constantValue(),
            row.constantValueType(),
            row.structuralType(),
            row.semanticTypeKind(),
            row.semanticTypeName(),
            row.semanticDisplayName(),
            row.locationClassName(),
            row.locationMethodName(),
            row.locationMethodDescriptor(),
            row.locationLineNumber(),
            row.confidence()))
        .collectList();
  }
}
