package org.glodean.constants.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;
import org.glodean.constants.model.ClassConstants;
import org.glodean.constants.store.postgres.PostgresService;
import org.glodean.constants.store.solr.SolrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CompositeClassConstantsStoreTest {

  @Mock SolrService solrService;
  @Mock PostgresService postgresService;
  @Mock VersionIncrementer versionIncrementer;

  CompositeClassConstantsStore store;

  @BeforeEach
  void setUp() {
    store = new CompositeClassConstantsStore(solrService, postgresService, versionIncrementer);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  static ClassConstants sample() {
    var loc = new UsageLocation("com/example/Greeter", "greet", "()V", 0, null);
    var usage =
        new ConstantUsage(
            UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.LOG_MESSAGE, loc, 0.9);
    var cc = new ClassConstant("Hello", Set.of(usage));
    return new ClassConstants("com/example/Greeter", Set.of(cc));
  }

  // ── store(constants, project) — auto-version ──────────────────────────────

  @Test
  void storeAutoVersionCallsIncrementor() {
    when(versionIncrementer.getNextVersion("proj", "com/example/Greeter")).thenReturn(5);
    when(postgresService.store(any(), eq("proj"), eq(5))).thenReturn(Mono.just(sample()));
    when(solrService.store(any(), eq("proj"), eq(5))).thenReturn(Mono.just(sample()));

    ClassConstants result = store.store(sample(), "proj").block();

    assertThat(result).isNotNull();
    assertThat(result.name()).isEqualTo("com/example/Greeter");
    verify(versionIncrementer).getNextVersion("proj", "com/example/Greeter");
    verify(postgresService).store(any(), eq("proj"), eq(5));
  }

  // ── store(constants, project, version) — explicit version ─────────────────

  @Test
  void storeExplicitVersionDualWritesBothStores() {
    when(postgresService.store(any(), eq("proj"), eq(3))).thenReturn(Mono.just(sample()));
    when(solrService.store(any(), eq("proj"), eq(3))).thenReturn(Mono.just(sample()));

    ClassConstants result = store.store(sample(), "proj", 3).block();

    assertThat(result).isNotNull();
    assertThat(result.name()).isEqualTo("com/example/Greeter");
    verify(postgresService).store(any(), eq("proj"), eq(3));
    verify(solrService).store(any(), eq("proj"), eq(3));
  }

  @Test
  void storeExplicitVersionReturnsOriginalConstants() {
    ClassConstants expected = sample();
    when(postgresService.store(any(), anyString(), anyInt())).thenReturn(Mono.just(expected));
    when(solrService.store(any(), anyString(), anyInt())).thenReturn(Mono.just(expected));

    ClassConstants result = store.store(expected, "proj", 7).block();

    assertThat(result).isSameAs(expected);
  }

  // ── Solr failure is non-fatal ──────────────────────────────────────────────

  @Test
  void solrFailureIsNonFatalWhenPostgresSucceeds() {
    when(postgresService.store(any(), anyString(), anyInt())).thenReturn(Mono.just(sample()));
    when(solrService.store(any(), anyString(), anyInt()))
        .thenReturn(Mono.error(new RuntimeException("Solr unavailable")));

    // operation must still complete successfully despite Solr failure
    ClassConstants result = store.store(sample(), "proj", 1).block();
    assertThat(result).isNotNull();
    assertThat(result.name()).isEqualTo("com/example/Greeter");
  }

  @Test
  void postgresFailurePropagatesError() {
    when(postgresService.store(any(), anyString(), anyInt()))
        .thenReturn(Mono.error(new RuntimeException("DB down")));
    when(solrService.store(any(), anyString(), anyInt())).thenReturn(Mono.just(sample()));

    assertThatThrownBy(() -> store.store(sample(), "proj", 1).block())
        .isInstanceOf(RuntimeException.class);
  }

  // ── find – delegates to PostgreSQL ────────────────────────────────────────

  @Test
  void findDelegatesToPostgresNotSolr() {
    Map<Object, Collection<ClassConstant.UsageType>> expected =
        Map.of("Hello", Set.of(UsageType.METHOD_INVOCATION_PARAMETER));
    when(postgresService.find("proj:com/example/Greeter:1")).thenReturn(Mono.just(expected));

    Map<Object, Collection<ClassConstant.UsageType>> result =
        store.find("proj:com/example/Greeter:1").block();

    assertThat(result).isSameAs(expected);
    verify(solrService, never()).find(anyString());
  }

  @Test
  void findPropagatesPostgresError() {
    when(postgresService.find(anyString()))
        .thenReturn(Mono.error(new IllegalArgumentException("Unknown class!")));

    assertThatThrownBy(() -> store.find("proj:com/example/Greeter:99").block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown class!");
  }

  // ── fuzzySearch – delegates to Solr ───────────────────────────────────────

  @Test
  void fuzzySearchDelegatesToSolrNotPostgres() {
    FuzzySearchResponse resp =
        new FuzzySearchResponse(
            List.of(new FuzzySearchHit("proj", "com/example/Greeter", 1, List.of("Hello"))), 1L);
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
}

