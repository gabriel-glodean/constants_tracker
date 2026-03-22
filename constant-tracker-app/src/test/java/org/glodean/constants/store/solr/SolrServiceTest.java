package org.glodean.constants.store.solr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.glodean.constants.dto.FuzzySearchResponse;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;
import org.glodean.constants.model.ClassConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SolrServiceTest {

  @Mock HttpSolrClientBase solrClient;

  SolrService svc;

  @BeforeEach
  void setUp() {
    svc = new SolrService(solrClient);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  static ClassConstants sampleConstants() {
    var location = new UsageLocation("com/example/Greeter", "greet", "()V", 5, null);
    var usage =
        new ConstantUsage(
            UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.LOG_MESSAGE, location, 0.9);
    var cc = new ClassConstant("Hello, world!", Set.of(usage));
    return new ClassConstants("com/example/Greeter", Set.of(cc));
  }

  static ClassConstants multiConstantClass() {
    var loc = new UsageLocation("com/example/Repo", "query", "()V", 10, null);
    var u1 =
        new ConstantUsage(
            UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.SQL_FRAGMENT, loc, 0.95);
    var u2 = new ConstantUsage(UsageType.FIELD_STORE, CoreSemanticType.LOG_MESSAGE, loc, 0.8);
    var cc1 = new ClassConstant("SELECT * FROM users", Set.of(u1));
    var cc2 = new ClassConstant("https://api.example.com", Set.of(u2));
    return new ClassConstants("com/example/Repo", Set.of(cc1, cc2));
  }

  // ── store(ClassConstants, String) – auto-version overload ─────────────────

  @Test
  void storeWithoutVersionThrowsUnsupported() {
    assertThatThrownBy(() -> svc.store(sampleConstants(), "proj"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // ── store(ClassConstants, String, int) ─────────────────────────────────────

  @Test
  void storeWithVersionSucceedsAndReturnsOriginalConstants() {
    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new NamedList<>()));

    ClassConstants result = svc.store(sampleConstants(), "proj", 1).block();
    assertThat(result).isNotNull();
    assertThat(result.name()).isEqualTo("com/example/Greeter");
  }

  @Test
  void storeWithMultipleConstantsIndexesAllPairs() {
    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new NamedList<>()));

    ClassConstants result = svc.store(multiConstantClass(), "proj", 2).block();
    assertThat(result).isNotNull();
    assertThat(result.name()).isEqualTo("com/example/Repo");
  }

  @Test
  void storeWithVersionPropagatesSolrError() {
    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Solr down")));

    assertThatThrownBy(() -> svc.store(sampleConstants(), "proj", 1).block())
        .isInstanceOf(RuntimeException.class);
  }

  // ── find(String) ───────────────────────────────────────────────────────────

  @Test
  void findReturnsParsedConstantPairs() {
    SolrDocumentList docs = buildDocsWith("hello|METHOD_INVOCATION_PARAMETER");
    NamedList<Object> response = new NamedList<>();
    response.add("response", docs);

    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(response));

    Map<Object, Collection<UsageType>> result =
        svc.find("proj:com/example/Greeter:1").block();

    assertThat(result).isNotNull().containsKey("hello");
    Collection<UsageType> usages = result.get("hello");
    assertThat(usages).contains(UsageType.METHOD_INVOCATION_PARAMETER);
  }

  @Test
  void findReturnsMultipleUsageTypesForSameValue() {
    SolrDocumentList docs = new SolrDocumentList();
    SolrDocument doc = new SolrDocument();
    doc.setField(
        "constant_pairs_ss",
        List.of("SELECT|METHOD_INVOCATION_PARAMETER", "SELECT|FIELD_STORE"));
    docs.add(doc);
    docs.setNumFound(1L);

    NamedList<Object> response = new NamedList<>();
    response.add("response", docs);

    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(response));

    Map<Object, Collection<UsageType>> result =
        svc.find("proj:com/example/Repo:1").block();

    assertThat(result).isNotNull();
    Collection<UsageType> usages = result.get("SELECT");
    assertThat(usages)
        .containsExactlyInAnyOrder(UsageType.METHOD_INVOCATION_PARAMETER, UsageType.FIELD_STORE);
  }

  @Test
  void findThrowsIllegalArgumentWhenDocListIsEmpty() {
    SolrDocumentList emptyDocs = new SolrDocumentList();
    emptyDocs.setNumFound(0L);

    NamedList<Object> response = new NamedList<>();
    response.add("response", emptyDocs);

    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(response));

    assertThatThrownBy(() -> svc.find("proj:com/example/Greeter:1").block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown class!");
  }

  @Test
  void findReturnsEmptyMapWhenResponseBlockAbsent() {
    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new NamedList<>()));

    Map<Object, Collection<UsageType>> result =
        svc.find("proj:com/example/Greeter:1").block();
    assertThat(result).isNotNull().isEmpty();
  }

  @Test
  void findIgnoresMalformedPairWithNoSeparator() {
    SolrDocumentList docs = new SolrDocumentList();
    SolrDocument doc = new SolrDocument();
    doc.setField("constant_pairs_ss", List.of("noSeparator", "|leadingSep"));
    docs.add(doc);
    docs.setNumFound(1L);

    NamedList<Object> response = new NamedList<>();
    response.add("response", docs);

    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(response));

    Map<Object, Collection<UsageType>> result =
        svc.find("proj:com/example/Greeter:1").block();
    assertThat(result).isNotNull().isEmpty();
  }

  @Test
  void findHandlesDocWithNoPairsField() {
    SolrDocumentList docs = new SolrDocumentList();
    SolrDocument doc = new SolrDocument(); // no constant_pairs_ss field at all
    docs.add(doc);
    docs.setNumFound(1L);

    NamedList<Object> response = new NamedList<>();
    response.add("response", docs);

    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(response));

    Map<Object, Collection<UsageType>> result =
        svc.find("proj:com/example/Greeter:1").block();
    assertThat(result).isNotNull().isEmpty();
  }

  // ── fuzzySearch(...) ───────────────────────────────────────────────────────

  @Test
  void fuzzySearchReturnsParsedHits() {
    SolrDocumentList docs = new SolrDocumentList();
    SolrDocument doc = new SolrDocument();
    doc.setField("project", "proj");
    doc.setField("class_name", "com/example/Repo");
    doc.setField("class_version", 1);
    doc.addField("constant_values_t", "SELECT * FROM users");
    docs.add(doc);
    docs.setNumFound(1L);

    NamedList<Object> response = new NamedList<>();
    response.add("response", docs);

    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(response));

    FuzzySearchResponse result = svc.fuzzySearch("proj", "SELECT", 0, 10).block();

    assertThat(result).isNotNull();
    assertThat(result.totalFound()).isEqualTo(1L);
    assertThat(result.hits()).hasSize(1);
    assertThat(result.hits().get(0).project()).isEqualTo("proj");
    assertThat(result.hits().get(0).className()).isEqualTo("com/example/Repo");
    assertThat(result.hits().get(0).version()).isEqualTo(1);
    assertThat(result.hits().get(0).constantValues()).contains("SELECT * FROM users");
  }

  @Test
  void fuzzySearchReturnsEmptyResponseWhenNoResponseBlock() {
    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new NamedList<>()));

    FuzzySearchResponse result = svc.fuzzySearch("proj", "SELECT", 1, 10).block();

    assertThat(result).isNotNull();
    assertThat(result.totalFound()).isEqualTo(0L);
    assertThat(result.hits()).isEmpty();
  }

  @Test
  void fuzzySearchWithEmptyDocumentListReturnsZeroResults() {
    SolrDocumentList emptyDocs = new SolrDocumentList();
    emptyDocs.setNumFound(0L);

    NamedList<Object> response = new NamedList<>();
    response.add("response", emptyDocs);

    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(response));

    FuzzySearchResponse result = svc.fuzzySearch("proj", "SELECT", 2, 10).block();

    assertThat(result).isNotNull();
    assertThat(result.totalFound()).isEqualTo(0L);
    assertThat(result.hits()).isEmpty();
  }

  @Test
  void fuzzySearchCrossProjectPassesStarProject() {
    // project="*" means no fq filter is added to the Solr query
    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new NamedList<>()));

    assertThatNoException()
        .isThrownBy(() -> svc.fuzzySearch("*", "SELECT", 1, 50).block());
  }

  @Test
  void fuzzySearchHandlesDocWithNullConstantValues() {
    SolrDocumentList docs = new SolrDocumentList();
    SolrDocument doc = new SolrDocument();
    doc.setField("project", "proj");
    doc.setField("class_name", "com/example/Empty");
    doc.setField("class_version", 3);
    // no constant_values_t field → getFieldValues returns null
    docs.add(doc);
    docs.setNumFound(1L);

    NamedList<Object> response = new NamedList<>();
    response.add("response", docs);

    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(response));

    FuzzySearchResponse result = svc.fuzzySearch("proj", "missing", 0, 10).block();

    assertThat(result).isNotNull();
    assertThat(result.hits()).hasSize(1);
    assertThat(result.hits().get(0).constantValues()).isEmpty();
  }

  @Test
  void fuzzySearchHandlesNonNumericVersion() {
    SolrDocumentList docs = new SolrDocumentList();
    SolrDocument doc = new SolrDocument();
    doc.setField("project", "proj");
    doc.setField("class_name", "com/example/Greeter");
    doc.setField("class_version", "not-a-number"); // non-Number version → defaults to 0
    docs.add(doc);
    docs.setNumFound(1L);

    NamedList<Object> response = new NamedList<>();
    response.add("response", docs);

    when(solrClient.requestAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(response));

    FuzzySearchResponse result = svc.fuzzySearch("proj", "test", 0, 10).block();

    assertThat(result).isNotNull();
    assertThat(result.hits().get(0).version()).isEqualTo(0);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private static SolrDocumentList buildDocsWith(String... pairs) {
    SolrDocumentList docs = new SolrDocumentList();
    SolrDocument doc = new SolrDocument();
    doc.setField("constant_pairs_ss", List.of(pairs));
    docs.add(doc);
    docs.setNumFound(docs.size());
    return docs;
  }
}

