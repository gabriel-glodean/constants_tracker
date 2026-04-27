package org.glodean.constants.store.solr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.glodean.constants.store.postgres.entity.SolrOutboxEntry;
import org.glodean.constants.store.postgres.repository.SolrOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SolrOutboxProcessorTest {

  @Mock SolrOutboxRepository outboxRepository;
  @Mock SolrService solrService;
  @Mock TransactionalOperator transactionalOperator;

  SolrOutboxProcessor processor;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    processor = new SolrOutboxProcessor(outboxRepository, solrService, transactionalOperator);
    // Make transactional(Mono) a transparent pass-through so the reactive pipeline
    // executes synchronously with in-memory mocks during tests
    lenient().when(transactionalOperator.transactional(any(Mono.class)))
        .thenAnswer(inv -> inv.getArgument(0, Mono.class));
    // deleteAllById is eagerly evaluated during chain construction even when the deletion
    // branch is skipped (e.g., Solr failure), so always stub it to avoid NPE
    lenient().when(outboxRepository.deleteAllById(any())).thenReturn(Mono.empty());
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  /** Builds a SolrOutboxEntry whose payloadJson is a valid, Jackson-parseable payload. */
  static SolrOutboxEntry validEntry(long id, String project, String path, int version) {
    try {
      var payload = new SolrOutboxPayload(
          project + ":" + path + ":" + version, project, path, version, "CLASS_FILE",
          List.of("Hello|METHOD_INVOCATION_PARAMETER"), List.of("Hello"), List.of());
      String json = new ObjectMapper().writeValueAsString(payload);
      OffsetDateTime now = OffsetDateTime.now();
      return new SolrOutboxEntry(id, now, project, path, version, json, 0, null, now);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static SolrOutboxEntry corruptEntry(long id) {
    OffsetDateTime now = OffsetDateTime.now();
    return new SolrOutboxEntry(id, now, "proj", "com/Foo", 1, "NOT_VALID_JSON{{", 0, null, now);
  }

  // ── drain — empty batch ────────────────────────────────────────────────────

  @Test
  void drainWithEmptyBatchDoesNotCallSolrOrDeleteAnyRows() {
    when(outboxRepository.claimBatch(any(), anyInt(), anyInt())).thenReturn(Flux.empty());

    processor.drain();

    verify(outboxRepository).claimBatch(any(), anyInt(), anyInt());
    verify(solrService, never()).storeDocumentBatch(anyList());
    verify(outboxRepository, never()).deleteAllById(anyList());
  }

  // ── drain — valid entries ──────────────────────────────────────────────────

  @Test
  void drainWithValidEntryCallsSolrAndDeletesRow() {
    var entry = validEntry(1L, "proj", "com/example/Foo", 1);
    when(outboxRepository.claimBatch(any(), anyInt(), anyInt())).thenReturn(Flux.just(entry));
    when(solrService.storeDocumentBatch(anyList())).thenReturn(Mono.empty());

    processor.drain();

    verify(solrService).storeDocumentBatch(anyList());
    verify(outboxRepository).deleteAllById(argThat(ids -> {
      List<?> list = (List<?>) ids;
      return list.size() == 1 && list.get(0).equals(1L);
    }));
  }

  @Test
  void drainMultipleValidEntriesSubmitsThemInOneBatch() {
    var e1 = validEntry(1L, "proj", "com/Foo", 1);
    var e2 = validEntry(2L, "proj", "com/Bar", 1);
    when(outboxRepository.claimBatch(any(), anyInt(), anyInt())).thenReturn(Flux.just(e1, e2));
    when(solrService.storeDocumentBatch(anyList())).thenReturn(Mono.empty());

    processor.drain();

    // Both docs should be submitted in a single Solr call
    verify(solrService).storeDocumentBatch(argThat(docs -> docs.size() == 2));
  }

  // ── drain — corrupt entries ────────────────────────────────────────────────

  @Test
  void drainCorruptEntryExhaustsItImmediatelyWithoutCallingSolr() {
    var corrupt = corruptEntry(42L);
    when(outboxRepository.claimBatch(any(), anyInt(), anyInt())).thenReturn(Flux.just(corrupt));
    when(outboxRepository.save(any())).thenReturn(Mono.just(corrupt));

    processor.drain();

    verify(solrService, never()).storeDocumentBatch(anyList());
    // The corrupt entry is saved with attempts = MAX_ATTEMPTS so it's picked up by compaction
    verify(outboxRepository).save(argThat(e -> e.attempts() == SolrOutboxProcessor.MAX_ATTEMPTS));
  }

  @Test
  void drainMixedValidAndCorruptEntriesIndexesValidAndExhaustsCorrupt() {
    var good = validEntry(1L, "proj", "com/Good", 1);
    var bad = corruptEntry(2L);
    when(outboxRepository.claimBatch(any(), anyInt(), anyInt())).thenReturn(Flux.just(good, bad));
    when(solrService.storeDocumentBatch(anyList())).thenReturn(Mono.empty());
    when(outboxRepository.deleteAllById(any())).thenReturn(Mono.empty());
    when(outboxRepository.save(any())).thenReturn(Mono.just(bad));

    processor.drain();

    // Good entry → Solr batch
    verify(solrService).storeDocumentBatch(argThat(docs -> docs.size() == 1));
    // Corrupt entry → exhausted in DB
    verify(outboxRepository).save(argThat(e -> e.attempts() == SolrOutboxProcessor.MAX_ATTEMPTS));
  }

  // ── drain — Solr failure ───────────────────────────────────────────────────

  @Test
  void drainWhenSolrFailsSchedulesRetryWithBackoffAndDoesNotThrow() {
    var entry = validEntry(5L, "proj", "com/Foo", 2);
    when(outboxRepository.claimBatch(any(), anyInt(), anyInt())).thenReturn(Flux.just(entry));
    when(solrService.storeDocumentBatch(anyList()))
        .thenReturn(Mono.error(new RuntimeException("Solr unavailable")));
    when(outboxRepository.save(any())).thenReturn(Mono.just(entry));

    processor.drain(); // must not propagate the exception

    // Entry should be re-saved with incremented attempts (back-off)
    verify(outboxRepository).save(argThat(e -> e.attempts() == 1 && e.lastError() != null));
    // Deletion must NOT be called — Solr failed before the delete step
    // (Note: deleteAllById IS called during chain construction but not executed —
    //  the lenient stub in setUp handles this transparently)
  }

  // ── compactDeadLetters ────────────────────────────────────────────────────

  @Test
  void compactDeadLettersMovesExhaustedRowsAndDeletesThem() {
    when(outboxRepository.moveExhaustedToDeadLetter(anyInt())).thenReturn(Mono.just(3));
    when(outboxRepository.deleteExhausted(anyInt())).thenReturn(Mono.just(3));

    processor.compactDeadLetters();

    verify(outboxRepository).moveExhaustedToDeadLetter(SolrOutboxProcessor.MAX_ATTEMPTS);
    verify(outboxRepository).deleteExhausted(SolrOutboxProcessor.MAX_ATTEMPTS);
  }

  @Test
  void compactDeadLettersWithNoExhaustedRowsIsNoOp() {
    when(outboxRepository.moveExhaustedToDeadLetter(anyInt())).thenReturn(Mono.just(0));
    when(outboxRepository.deleteExhausted(anyInt())).thenReturn(Mono.just(0));

    processor.compactDeadLetters(); // should not throw

    verify(outboxRepository).moveExhaustedToDeadLetter(SolrOutboxProcessor.MAX_ATTEMPTS);
    verify(outboxRepository).deleteExhausted(SolrOutboxProcessor.MAX_ATTEMPTS);
  }

  // ── MAX_ATTEMPTS constant ─────────────────────────────────────────────────

  @Test
  void maxAttemptsConstantIsPositive() {
    assertThat(SolrOutboxProcessor.MAX_ATTEMPTS).isGreaterThan(0);
  }
}
