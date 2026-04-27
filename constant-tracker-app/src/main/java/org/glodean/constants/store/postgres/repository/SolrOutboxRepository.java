package org.glodean.constants.store.postgres.repository;

import java.time.OffsetDateTime;

import org.glodean.constants.store.postgres.entity.SolrOutboxEntry;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for {@link SolrOutboxEntry}.
 *
 * {@link #claimBatch} uses {@code FOR UPDATE SKIP LOCKED} to safely claim rows even under
 * concurrent drain cycles; it must therefore be called within a reactive transaction.
 *
 * <p>{@link #moveExhaustedToDeadLetter} and {@link #deleteExhausted} should always be called
 * together inside the same transaction so that no row is lost or double-archived.
 */
public interface SolrOutboxRepository extends ReactiveCrudRepository<SolrOutboxEntry, Long> {

  /**
   * Selects up to {@code batchSize} rows that are ready to process (next_attempt_at &le; now
   * and attempts &lt; maxAttempts) and locks them for the duration of the calling transaction
   * ({@code FOR UPDATE SKIP LOCKED}).
   *
   * @param now        current timestamp
   * @param maxAttempts maximum allowed attempt count (exclusive upper bound)
   * @param batchSize  maximum number of rows to claim
   * @return locked, ready-to-process rows
   */
  @Query(
      "SELECT * FROM solr_outbox "
          + "WHERE next_attempt_at <= :now AND attempts < :maxAttempts "
          + "LIMIT :batchSize FOR UPDATE SKIP LOCKED")
  Flux<SolrOutboxEntry> claimBatch(OffsetDateTime now, int maxAttempts, int batchSize);

  /**
   * Archives exhausted rows (attempts &ge; maxAttempts) to {@code solr_outbox_dead}.
   * Must be followed by {@link #deleteExhausted} in the same transaction.
   *
   * @param maxAttempts minimum attempt count that qualifies as exhausted
   * @return number of rows archived
   */
  @Modifying
  @Query(
      "INSERT INTO solr_outbox_dead (id, project, unit_path, version, payload_json, last_error) "
          + "SELECT id, project, unit_path, version, payload_json, last_error "
          + "FROM solr_outbox WHERE attempts >= :maxAttempts")
  Mono<Integer> moveExhaustedToDeadLetter(int maxAttempts);

  /**
   * Removes exhausted rows from the live outbox table. Should be called after
   * {@link #moveExhaustedToDeadLetter} in the same transaction.
   *
   * @param maxAttempts minimum attempt count that qualifies as exhausted
   * @return number of rows deleted
   */
  @Modifying
  @Query("DELETE FROM solr_outbox WHERE attempts >= :maxAttempts")
  Mono<Integer> deleteExhausted(int maxAttempts);
}
