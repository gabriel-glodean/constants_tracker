package org.glodean.constants.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO for {@code GET /jar/jobs} — represents the current extraction
 * lifecycle state of a single fat JAR upload job.
 *
 * @param project         project identifier
 * @param version         project version the extraction was registered under
 * @param jarName         name of the uploaded fat JAR
 * @param status          current lifecycle status: {@code STARTED}, {@code COMPLETED}, or {@code FAILED}
 * @param startedAt       timestamp when the extraction job was created
 * @param lastUpdatedAt   timestamp of the last status or counter update
 * @param nestedTotal     total number of nested JARs discovered inside the fat JAR
 * @param nestedProcessed number of nested JARs successfully extracted
 * @param nestedFailed    number of nested JARs that failed extraction (skipped with warning)
 * @param errorMessage    the error message if {@code status} is {@code FAILED}; {@code null} otherwise
 */
public record JarExtractionStatusResponse(
    String project,
    int version,
    String jarName,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime lastUpdatedAt,
    int nestedTotal,
    int nestedProcessed,
    int nestedFailed,
    String errorMessage) {

  /** Maps a {@link org.glodean.constants.store.postgres.entity.JarExtractionEntity} to this DTO. */
  public static JarExtractionStatusResponse from(
      org.glodean.constants.store.postgres.entity.JarExtractionEntity entity) {
    return new JarExtractionStatusResponse(
        entity.project(),
        entity.version(),
        entity.jarName(),
        entity.status(),
        entity.startedAt(),
        entity.lastUpdatedAt(),
        entity.nestedTotal(),
        entity.nestedProcessed(),
        entity.nestedFailed(),
        entity.errorMessage());
  }
}
