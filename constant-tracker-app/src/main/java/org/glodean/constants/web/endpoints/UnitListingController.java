package org.glodean.constants.web.endpoints;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.glodean.constants.dto.UnitListingResponse;
import org.glodean.constants.store.postgres.repository.UnitSnapshotRepository;
import org.glodean.constants.web.validation.ValidProjectName;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Read-only endpoint that lists extracted classes/config files and their constant counts.
 */
@Validated
@RestController
@RequestMapping("/units")
public class UnitListingController {

  private final UnitSnapshotRepository unitSnapshotRepository;

  public UnitListingController(UnitSnapshotRepository unitSnapshotRepository) {
    this.unitSnapshotRepository = unitSnapshotRepository;
  }

  @PreAuthorize("isAuthenticated()")
  @GetMapping
  public Mono<List<UnitListingResponse>> listUnits(
      @NotBlank @ValidProjectName @RequestParam("project") String project,
      @Positive @RequestParam("version") int version) {
    return unitSnapshotRepository.findUnitConstantCountsByProjectAndVersion(project.strip(), version)
        .collectList()
        .map(rows -> {
          Map<String, List<UnitListingResponse.UnitEntry>> grouped = new LinkedHashMap<>();
          for (var row : rows) {
            grouped.computeIfAbsent(row.getUnitPath(), ignored -> new ArrayList<>())
                .add(new UnitListingResponse.UnitEntry(
                    row.getName(),
                    row.getConstants() == null ? 0L : row.getConstants()));
          }
          return grouped.entrySet().stream()
              .map(e -> new UnitListingResponse(e.getKey(), e.getValue()))
              .toList();
        });
  }
}

