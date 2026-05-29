package org.glodean.constants.web.endpoints;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.glodean.constants.dto.UnitListingRequest;
import org.glodean.constants.dto.UnitListingResponse;
import org.glodean.constants.store.postgres.UnitConstantQueries;
import org.glodean.constants.store.postgres.repository.UnitSnapshotRepository;
import org.glodean.constants.store.postgres.repository.projection.UnitConstantsCountRow;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Read-only endpoint that lists extracted classes/config files and their constant counts.
 *
 * <p>Optional filters ({@code structuralType}, {@code semanticTypeKind}, {@code semanticTypeName})
 * restrict the result to units that contain at least one matching constant usage.  When filters are
 * present the response is paged via {@code page} / {@code pageSize}; without filters the full
 * result set is returned (unchanged pre-existing behaviour).
 */
@Validated
@RestController
@RequestMapping("/units")
public class UnitListingController {

  private static final int DEFAULT_PAGE_SIZE = 50;

  private final UnitSnapshotRepository unitSnapshotRepository;
  private final UnitConstantQueries unitConstantQueries;

  public UnitListingController(
      UnitSnapshotRepository unitSnapshotRepository,
      UnitConstantQueries unitConstantQueries) {
    this.unitSnapshotRepository = unitSnapshotRepository;
    this.unitConstantQueries = unitConstantQueries;
  }

  @GetMapping
  public Mono<List<UnitListingResponse>> listUnits(@Valid @ModelAttribute UnitListingRequest req) {
    String proj = req.project().strip();

    Flux<UnitConstantsCountRow> rows = req.isFiltered()
        ? unitConstantQueries.unitCounts(req, DEFAULT_PAGE_SIZE)
        : unitSnapshotRepository.findUnitConstantCountsByProjectAndVersion(proj, req.version());

    return rows.collectList().map(this::groupRows);
  }

  private List<UnitListingResponse> groupRows(List<UnitConstantsCountRow> rows) {
    Map<String, List<UnitListingResponse.UnitEntry>> grouped = new LinkedHashMap<>();
    for (var row : rows) {
      grouped.computeIfAbsent(row.path(), ignored -> new ArrayList<>())
          .add(new UnitListingResponse.UnitEntry(
              row.name(),
              row.constants() == null ? 0L : row.constants()));
    }
    return grouped.entrySet().stream()
        .map(e -> new UnitListingResponse(e.getKey(), e.getValue()))
        .toList();
  }
}
