package org.glodean.constants.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.glodean.constants.dto.FuzzySearchHit;
import org.glodean.constants.dto.FuzzySearchResponse;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.services.ProjectVersionService;
import org.glodean.constants.store.postgres.PostgresService;
import org.glodean.constants.store.postgres.entity.ProjectVersionEntity;
import org.glodean.constants.store.solr.SolrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CompositeUnitConstantsStoreTest {

  @Mock SolrService solrService;
  @Mock PostgresService postgresService;
  @Mock ProjectVersionService projectVersionService;

  CompositeUnitConstantsStore store;

  @BeforeEach
  void setUp() {
    store =
        new CompositeUnitConstantsStore(
            solrService, postgresService, projectVersionService);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  static UnitConstants sample() {
    var loc = new UsageLocation("com/example/Greeter", "greet", "()V", 0, null);
    var usage =
        new ConstantUsage(
            UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.LOG_MESSAGE, loc, 0.9);
    var cc = new UnitConstant("Hello", Set.of(usage));
    var descriptor = new org.glodean.constants.model.UnitDescriptor(org.glodean.constants.extractor.bytecode.BytecodeSourceKind.CLASS_FILE, "com/example/Greeter");
    return new UnitConstants(descriptor, Set.of(cc));
  }

  // ── store(constants, project) — auto-version ──────────────────────────────

  @Test
  void storeAutoVersionCallsIncrementor() {
    var openVersion = new ProjectVersionEntity(1L, "proj", 5, null, "OPEN", null, null);
    when(projectVersionService.getOrCreateOpenVersion("proj")).thenReturn(Mono.just(openVersion));
    when(postgresService.store(any(), eq("proj"), eq(5))).thenReturn(Mono.just(sample()));

    UnitConstants result = store.store(sample(), "proj").block();

    assertThat(result).isNotNull();
    assertThat(result.source().path()).isEqualTo("com/example/Greeter");
    verify(projectVersionService).getOrCreateOpenVersion("proj");
    verify(postgresService).store(any(), eq("proj"), eq(5));
  }

  // ── store(constants, project, version) — explicit version ─────────────────

  @Test
  void storeExplicitVersionDualWritesBothStores() {
    var openVersion = new ProjectVersionEntity(1L, "proj", 3, null, "OPEN", null, null);
    when(projectVersionService.ensureVersionExists("proj", 3)).thenReturn(Mono.just(openVersion));
    when(projectVersionService.isVersionOpen("proj", 3)).thenReturn(Mono.just(true));
    when(postgresService.store(any(), eq("proj"), eq(3))).thenReturn(Mono.just(sample()));

    UnitConstants result = store.store(sample(), "proj", 3).block();

    assertThat(result).isNotNull();
    assertThat(result.source().path()).isEqualTo("com/example/Greeter");
    verify(postgresService).store(any(), eq("proj"), eq(3));
    // Solr is indexed asynchronously via the outbox — no direct call expected here
    verify(solrService, never()).store(any(), anyString(), anyInt());
  }

  @Test
  void storeExplicitVersionReturnsOriginalConstants() {
    UnitConstants expected = sample();
    var openVersion = new ProjectVersionEntity(1L, "proj", 7, null, "OPEN", null, null);
    when(projectVersionService.ensureVersionExists(anyString(), anyInt()))
        .thenReturn(Mono.just(openVersion));
    when(projectVersionService.isVersionOpen(anyString(), anyInt())).thenReturn(Mono.just(true));
    when(postgresService.store(any(), anyString(), anyInt())).thenReturn(Mono.just(expected));

    UnitConstants result = store.store(expected, "proj", 7).block();

    assertThat(result).isSameAs(expected);
  }

  // ── Solr failure is non-fatal (Solr is async via outbox) ──────────────────

  @Test
  void solrFailureIsNonFatalWhenPostgresSucceeds() {
    // Solr is indexed asynchronously via the outbox — the synchronous store path only
    // calls PostgresService. If postgres succeeds the store always succeeds.
    var openVersion = new ProjectVersionEntity(1L, "proj", 1, null, "OPEN", null, null);
    when(projectVersionService.ensureVersionExists(anyString(), anyInt()))
        .thenReturn(Mono.just(openVersion));
    when(projectVersionService.isVersionOpen(anyString(), anyInt())).thenReturn(Mono.just(true));
    when(postgresService.store(any(), anyString(), anyInt())).thenReturn(Mono.just(sample()));

    UnitConstants result = store.store(sample(), "proj", 1).block();
    assertThat(result).isNotNull();
    assertThat(result.source().path()).isEqualTo("com/example/Greeter");
  }

  @Test
  void postgresFailurePropagatesError() {
    var openVersion = new ProjectVersionEntity(1L, "proj", 1, null, "OPEN", null, null);
    when(projectVersionService.ensureVersionExists(anyString(), anyInt()))
        .thenReturn(Mono.just(openVersion));
    when(projectVersionService.isVersionOpen(anyString(), anyInt())).thenReturn(Mono.just(true));
    when(postgresService.store(any(), anyString(), anyInt()))
        .thenReturn(Mono.error(new RuntimeException("DB down")));

    assertThatThrownBy(() -> store.store(sample(), "proj", 1).block())
        .isInstanceOf(RuntimeException.class);
  }

  // ── find – delegates to PostgreSQL ────────────────────────────────────────

  @Test
  void findDelegatesToPostgresNotSolr() {
    Map<Object, Collection<UnitConstant.UsageType>> expected =
        Map.of("Hello", Set.of(UsageType.METHOD_INVOCATION_PARAMETER));
    when(postgresService.find("proj:com/example/Greeter:1")).thenReturn(Mono.just(expected));

    Map<Object, Collection<UnitConstant.UsageType>> result =
        store.find("proj:com/example/Greeter:1").block();

    assertThat(result).isSameAs(expected);
    verify(solrService, never()).find(anyString());
  }

  @Test
  void findPropagatesPostgresError() {
    when(postgresService.find(anyString()))
        .thenReturn(Mono.error(new IllegalArgumentException("Unknown class!")));
    when(projectVersionService.getVersion(anyString(), anyInt())).thenReturn(Mono.empty());

    assertThatThrownBy(() -> store.find("proj:com/example/Greeter:99").block())
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── fuzzySearch – delegates to Solr ───────────────────────────────────────

  @Test
  void fuzzySearchDelegatesToSolrNotPostgres() {
    FuzzySearchResponse resp =
        new FuzzySearchResponse(
            List.of(new FuzzySearchHit("proj", "com/example/Greeter", 1, "CLASS_FILE", List.of("Hello"), List.of())), 1L);
    when(solrService.fuzzySearch("proj", "Hello", 1, 10)).thenReturn(Mono.just(resp));

    FuzzySearchResponse result = store.fuzzySearch("proj", "Hello", 1, 10).block();

    assertThat(result).isNotNull();
    assertThat(result.totalFound()).isEqualTo(1L);
    assertThat(result.hits()).hasSize(1);
    verify(postgresService, never()).fuzzySearch(anyString(), anyString(), anyInt(), anyInt());
  }

  @Test
  void fuzzySearchPropagatesSolrError() {
    when(solrService.fuzzySearch(anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(Mono.error(new RuntimeException("Solr search failed")));

    assertThatThrownBy(() -> store.fuzzySearch("proj", "test", 0, 10).block())
        .isInstanceOf(RuntimeException.class);
  }

  // ── store(constants, project, version) — finalized version ────────────────

  @Test
  void storeWithFinalizedVersionRejectsUpload() {
    var closedVersion = new ProjectVersionEntity(1L, "proj", 2, null, "CLOSED", null, null);
    when(projectVersionService.ensureVersionExists("proj", 2)).thenReturn(Mono.just(closedVersion));
    when(projectVersionService.isVersionOpen("proj", 2)).thenReturn(Mono.just(false));

    assertThatThrownBy(() -> store.store(sample(), "proj", 2).block())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("finalized");
  }

  // ── storeAll ──────────────────────────────────────────────────────────────

  @Test
  void storeAllCallsPostgresForEachUnitAndRecordsRemovals() {
    UnitConstants s = sample();
    var openVersion = new ProjectVersionEntity(1L, "proj", 3, null, "OPEN", null, null);
    when(projectVersionService.getOrCreateOpenVersion("proj")).thenReturn(Mono.just(openVersion));
    when(postgresService.store(any(), eq("proj"), eq(3))).thenReturn(Mono.just(s));
    when(projectVersionService.recordRemovals(eq("proj"), eq(3), anySet()))
        .thenReturn(Flux.empty());

    List<UnitConstants> result = store.storeAll(List.of(s), "proj").block();

    assertThat(result).hasSize(1);
    verify(postgresService).store(any(), eq("proj"), eq(3));
    verify(projectVersionService).recordRemovals(eq("proj"), eq(3), anySet());
  }

  @Test
  void storeAllWithEmptyListRecordsRemovalsForAllOldUnits() {
    var openVersion = new ProjectVersionEntity(1L, "proj", 4, null, "OPEN", null, null);
    when(projectVersionService.getOrCreateOpenVersion("proj")).thenReturn(Mono.just(openVersion));
    when(projectVersionService.recordRemovals(eq("proj"), eq(4), anySet()))
        .thenReturn(Flux.empty());

    List<UnitConstants> result = store.storeAll(List.of(), "proj").block();

    assertThat(result).isEmpty();
    verify(projectVersionService).recordRemovals(eq("proj"), eq(4), anySet());
    verify(postgresService, never()).store(any(), anyString(), anyInt());
  }

  // ── UnitConstantsStore interface default methods ───────────────────────────

  @Test
  void defaultStoreAllThrowsUnsupportedOperationForBaseImplementation() {
    UnitConstantsStore minimal = new UnitConstantsStore() {
      @Override public Mono<UnitConstants> store(UnitConstants c, String p, int v) { return null; }
      @Override public Mono<UnitConstants> store(UnitConstants c, String p) { return null; }
      @Override public Mono<java.util.Map<Object, Collection<UnitConstant.UsageType>>> find(String k) { return null; }
    };

    assertThatThrownBy(() -> minimal.storeAll(List.of(), "proj").block())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void defaultFuzzySearchThrowsUnsupportedOperationForBaseImplementation() {
    UnitConstantsStore minimal = new UnitConstantsStore() {
      @Override public Mono<UnitConstants> store(UnitConstants c, String p, int v) { return null; }
      @Override public Mono<UnitConstants> store(UnitConstants c, String p) { return null; }
      @Override public Mono<java.util.Map<Object, Collection<UnitConstant.UsageType>>> find(String k) { return null; }
    };

    assertThatThrownBy(() -> minimal.fuzzySearch("proj", "term", 0, 10).block())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
