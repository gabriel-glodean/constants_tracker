package org.glodean.constants.store.postgres.entity;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity for the {@code solr_outbox} table.
 *
 * <p>Each row represents one pending or retrying Solr index operation. Successfully processed
 * Successfully processed rows are deleted immediately. Exhausted rows ({@code attempts >= MAX_ATTEMPTS})
 * are archived to {@code solr_outbox_dead} by the nightly compaction job.
 */
@Table("solr_outbox")
public record SolrOutboxEntry(
    @Id Long id,
    OffsetDateTime createdAt,
    String project,
    String unitPath,
    int version,
    String payloadJson,
    int attempts,
    String lastError,
    OffsetDateTime nextAttemptAt) {

  /** Factory for a brand-new outbox entry with zero attempts, scheduled to run immediately. */
  public static SolrOutboxEntry newEntry(
      String project, String unitPath, int version, String payloadJson) {
    OffsetDateTime now = OffsetDateTime.now();
    return new SolrOutboxEntry(null, now, project, unitPath, version, payloadJson, 0, null, now);
  }

  /**
   * Returns a copy with an incremented attempt count and an exponential back-off delay.
   * The delay is {@code 2^attempts} seconds, capped at 1 hour.
   *
   * @param error the error message from the failed attempt
   * @return updated entry ready to be saved
   */
  public SolrOutboxEntry withFailure(String error) {
    int nextAttempts = this.attempts + 1;
    long delaySeconds = Math.min((long) Math.pow(2, nextAttempts), 3600L);
    return new SolrOutboxEntry(
        id,
        createdAt,
        project,
        unitPath,
        version,
        payloadJson,
        nextAttempts,
        error,
        OffsetDateTime.now().plusSeconds(delaySeconds));
  }
}
