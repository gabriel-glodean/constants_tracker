package org.glodean.constants.store.solr;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrInputDocument;
import org.glodean.constants.store.postgres.entity.SolrOutboxEntry;
import org.glodean.constants.store.postgres.repository.SolrOutboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Scheduled background component that drains the {@code solr_outbox} table into Solr.
 *
 * <h2>Drain cycle (every {@code constants.solr.outbox.drain-interval-ms}, default 5 s)</h2>
 * <ol>
 *   <li>Claims up to {@value #BATCH_SIZE} ready rows using {@code FOR UPDATE SKIP LOCKED} inside
 *       a reactive transaction so concurrent app instances never pick the same rows.</li>
 *   <li>Separates corrupt (un-parseable payload) rows from valid ones. Corrupt rows are
 *       immediately maxed out ({@code attempts = MAX_ATTEMPTS}) so the nightly compaction
 *       archives them to the dead-letter table.</li>
 *   <li>Submits all valid {@link SolrInputDocument}s as a single Solr {@code UpdateRequest}
 *       with a commit.</li>
 *   <li>On success: deletes the processed rows from the outbox.</li>
 *   <li>On Solr failure: increments {@code attempts} and schedules the next retry with
 *       exponential back-off (2^n seconds, capped at 1 hour).</li>
 * </ol>
 *
 * <h2>Compaction (cron {@code constants.solr.outbox.compaction-cron}, default nightly 03:00)</h2>
 * <p>Moves exhausted rows ({@code attempts >= MAX_ATTEMPTS}) to {@code solr_outbox_dead}
 * and deletes them from the live outbox — both in a single transaction to prevent loss.</p>
 */
@Component
public class SolrOutboxProcessor {

  private static final Logger logger = LogManager.getLogger(SolrOutboxProcessor.class);
  private static final int BATCH_SIZE = 50;
  static final int MAX_ATTEMPTS = 10;

  /** Plain mapper — {@link SolrOutboxPayload} only contains basic types; no mixins needed. */
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final SolrOutboxRepository outboxRepository;
  private final SolrService solrService;
  private final TransactionalOperator transactionalOperator;

  public SolrOutboxProcessor(
      @Autowired SolrOutboxRepository outboxRepository,
      @Autowired SolrService solrService,
      @Autowired TransactionalOperator transactionalOperator) {
    this.outboxRepository = outboxRepository;
    this.solrService = solrService;
    this.transactionalOperator = transactionalOperator;
  }

  // -------------------------------------------------------------------------
  // Drain
  // -------------------------------------------------------------------------

  /**
   * Claims a batch of ready outbox rows, submits them to Solr, and deletes the successfully
   * indexed rows. Retries are scheduled automatically via exponential back-off on failure.
   */
  @Scheduled(fixedRateString = "${constants.solr.outbox.drain-interval-ms:1000}")
  public void drain() {
    Mono<Integer> work =
        outboxRepository
            .claimBatch(OffsetDateTime.now(), MAX_ATTEMPTS, BATCH_SIZE)
            .collectList()
            .flatMap(this::processBatch);

    transactionalOperator
        .transactional(work)
        .subscribe(
            count -> {
              if (count > 0) logger.atInfo().log("Solr outbox: flushed {} doc(s)", count);
            },
            e -> logger.atError().withThrowable(e).log("Solr outbox drain cycle failed"));
  }

  private Mono<Integer> processBatch(List<SolrOutboxEntry> batch) {
    if (batch.isEmpty()) {
      return Mono.just(0);
    }

    List<SolrOutboxEntry> good = new ArrayList<>(batch.size());
    List<SolrOutboxEntry> corrupt = new ArrayList<>();
    List<SolrInputDocument> docs = new ArrayList<>(batch.size());

    for (SolrOutboxEntry entry : batch) {
      try {
        docs.add(MAPPER.readValue(entry.payloadJson(), SolrOutboxPayload.class).toSolrDocument());
        good.add(entry);
      } catch (Exception e) {
        logger.atError().withThrowable(e).log(
            "Corrupt Solr outbox payload for {}:{}:{} — exhausting entry immediately",
            entry.project(), entry.unitPath(), entry.version());
        // Set attempts to MAX so the nightly compaction archives it to dead-letter
        corrupt.add(new SolrOutboxEntry(
            entry.id(), entry.createdAt(), entry.project(), entry.unitPath(), entry.version(),
            entry.payloadJson(), MAX_ATTEMPTS,
            "Corrupt payload: " + e.getMessage(), OffsetDateTime.now()));
      }
    }

    Mono<Void> exhaustCorrupt = corrupt.isEmpty()
        ? Mono.empty()
        : Flux.fromIterable(corrupt).flatMap(outboxRepository::save).then();

    if (good.isEmpty()) {
      return exhaustCorrupt.thenReturn(0);
    }

    return solrService
        .storeDocumentBatch(docs)
        .then(outboxRepository.deleteAllById(good.stream().map(SolrOutboxEntry::id).toList()))
        .then(exhaustCorrupt)
        .thenReturn(good.size())
        .onErrorResume(ex -> {
          logger.atWarn().withThrowable(ex).log(
              "Solr batch write failed ({} doc(s)) — scheduling retries with back-off",
              good.size());
          String errMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
          return Flux.fromIterable(good)
              .flatMap(e -> outboxRepository.save(e.withFailure(errMsg)))
              .then(exhaustCorrupt)
              .thenReturn(0);
        });
  }

  // -------------------------------------------------------------------------
  // Compaction
  // -------------------------------------------------------------------------

  /**
   * Nightly compaction: moves exhausted rows ({@code attempts >= MAX_ATTEMPTS}) from the live
   * outbox to the {@code solr_outbox_dead} dead-letter table, then removes them from
   * {@code solr_outbox}. Both operations run in the same transaction.
   *
   * <p>Dead-letter rows are never automatically deleted and can be replayed manually via SQL
   * or a future admin endpoint.
   */
  @Scheduled(cron = "${constants.solr.outbox.compaction-cron:0 0 3 * * *}")
  public void compactDeadLetters() {
    Mono<Void> work = outboxRepository
        .moveExhaustedToDeadLetter(MAX_ATTEMPTS)
        .doOnNext(n -> {
          if (n > 0) logger.atWarn().log("Solr outbox compaction: archived {} dead-letter row(s)", n);
        })
        .then(outboxRepository.deleteExhausted(MAX_ATTEMPTS))
        .doOnNext(n -> {
          if (n > 0) logger.atInfo().log("Solr outbox compaction: removed {} exhausted row(s)", n);
        })
        .then();

    transactionalOperator
        .transactional(work)
        .subscribe(
            _ -> {},
            e -> logger.atError().withThrowable(e).log("Solr outbox compaction failed"));
  }
}
