package org.glodean.constants.store.postgres;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.BiFunction;
import java.util.Set;
import org.glodean.constants.dto.UnitListingRequest;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.store.SemanticTypeStore;
import org.glodean.constants.store.postgres.repository.projection.ConstantDetailRow;
import org.glodean.constants.store.postgres.repository.projection.UnitConstantsCountRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UnitConstantQueriesTest {

  @Mock DatabaseClient db;
  @Mock DatabaseClient.GenericExecuteSpec spec;
  @Mock SemanticTypeStore semanticTypeStore;

  private UnitConstantQueries queries;

  @BeforeEach
  void setUp() {
    queries = new UnitConstantQueries(db, semanticTypeStore);
    lenient().when(db.sql(anyString())).thenReturn(spec);
    lenient().when(spec.bind(anyString(), any())).thenReturn(spec);
  }

  // ── unitCounts — base path ────────────────────────────────────────────────────

  @Test
  void unitCounts_noFilters_returnsRows() {
    stubCountSpec(Flux.just(new UnitConstantsCountRow("a.jar", "Foo.class", 3L)));

    StepVerifier.create(queries.unitCounts(req(null, null, null), 50))
        .expectNextMatches(r -> "Foo.class".equals(r.name()) && r.constants() == 3L)
        .verifyComplete();

    verify(spec).bind(eq("project"), eq("proj"));
    verify(spec).bind(eq("version"), eq(1));
    verify(spec).bind(eq("limit"),   eq(50));
    verify(spec).bind(eq("offset"),  eq(0L));
  }

  @Test
  void unitCounts_customPageAndPageSize_bindsCorrectOffsetAndLimit() {
    stubCountSpec(Flux.empty());
    // page=2, pageSize=5 → offset = 10
    var req = new UnitListingRequest("proj", 1, null, null, null, 2, 5);
    StepVerifier.create(queries.unitCounts(req, 50)).verifyComplete();

    verify(spec).bind(eq("limit"),  eq(5));
    verify(spec).bind(eq("offset"), eq(10L));
  }

  // ── unitCounts — structuralType filter ───────────────────────────────────────

  @Test
  void unitCounts_withStructuralTypeFilter_bindsParam() {
    stubCountSpec(Flux.empty());

    StepVerifier.create(queries.unitCounts(req("METHOD_INVOCATION_PARAMETER", null, null), 50))
        .verifyComplete();

    verify(spec).bind(eq("structuralType"), eq("METHOD_INVOCATION_PARAMETER"));
  }

  // ── unitCounts — semanticType filter (three branches) ────────────────────────

  @Test
  void unitCounts_withKnownCoreSemanticType_bindsCoreKindAndName() {
    stubCountSpec(Flux.empty());
    when(semanticTypeStore.getSupportedSemanticTypes())
        .thenReturn(Set.of(UnitConstant.CoreSemanticType.LOG_MESSAGE));

    StepVerifier.create(queries.unitCounts(req(null, "LOG_MESSAGE", null), 50)).verifyComplete();

    verify(spec).bind(eq("semanticTypeKind"), eq("CORE"));
    verify(spec).bind(eq("semanticTypeName"), eq("LOG_MESSAGE"));
  }

  @Test
  void unitCounts_withCustomSemanticType_bindsCustomKindAndName() {
    var custom = new UnitConstant.CustomSemanticType("my-type", "My Type", "custom desc");
    stubCountSpec(Flux.empty());
    when(semanticTypeStore.getSupportedSemanticTypes()).thenReturn(Set.of(custom));

    StepVerifier.create(queries.unitCounts(req(null, "my-type", null), 50)).verifyComplete();

    verify(spec).bind(eq("semanticTypeKind"), eq("CUSTOM"));
    verify(spec).bind(eq("semanticTypeName"), eq("my-type"));
  }

  @Test
  void unitCounts_withUnknownSemanticType_usesSentinelKind() {
    stubCountSpec(Flux.empty());
    when(semanticTypeStore.getSupportedSemanticTypes()).thenReturn(Set.of());

    StepVerifier.create(queries.unitCounts(req(null, "NON_EXISTENT", null), 50)).verifyComplete();

    verify(spec).bind(eq("semanticTypeKind"), eq("__UNKNOWN__"));
    verify(spec).bind(eq("semanticTypeName"), eq("NON_EXISTENT"));
  }

  // ── unitCounts — constantValueType filter ────────────────────────────────────

  @Test
  void unitCounts_withConstantValueTypeFilter_bindsParam() {
    stubCountSpec(Flux.empty());

    StepVerifier.create(queries.unitCounts(req(null, null, "String"), 50)).verifyComplete();

    verify(spec).bind(eq("constantValueType"), eq("String"));
  }

  // ── unitCounts — all filters combined ────────────────────────────────────────

  @Test
  void unitCounts_allFiltersCombined_bindsAllParams() {
    stubCountSpec(Flux.empty());
    when(semanticTypeStore.getSupportedSemanticTypes())
        .thenReturn(Set.of(UnitConstant.CoreSemanticType.URL_RESOURCE));

    var req = new UnitListingRequest("proj", 1, "FIELD_STORE", "URL_RESOURCE", "String", null, null);
    StepVerifier.create(queries.unitCounts(req, 50)).verifyComplete();

    verify(spec).bind(eq("structuralType"),    eq("FIELD_STORE"));
    verify(spec).bind(eq("semanticTypeKind"),  eq("CORE"));
    verify(spec).bind(eq("semanticTypeName"),  eq("URL_RESOURCE"));
    verify(spec).bind(eq("constantValueType"), eq("String"));
  }

  // ── constantDetails ───────────────────────────────────────────────────────────

  @Test
  void constantDetails_noFilters_returnsRows() {
    stubDetailSpec(Flux.just(detailRow("hello")));

    StepVerifier.create(queries.constantDetails(req(null, null, null), 100))
        .expectNextMatches(r -> "hello".equals(r.constantValue()))
        .verifyComplete();
  }

  @Test
  void constantDetails_allFilters_bindsAllParams() {
    stubDetailSpec(Flux.empty());
    when(semanticTypeStore.getSupportedSemanticTypes())
        .thenReturn(Set.of(UnitConstant.CoreSemanticType.SQL_FRAGMENT));

    var req = new UnitListingRequest("proj", 1, "STATIC_FIELD", "SQL_FRAGMENT", "String", null, null);
    StepVerifier.create(queries.constantDetails(req, 100)).verifyComplete();

    verify(spec).bind(eq("structuralType"),    eq("STATIC_FIELD"));
    verify(spec).bind(eq("semanticTypeKind"),  eq("CORE"));
    verify(spec).bind(eq("semanticTypeName"),  eq("SQL_FRAGMENT"));
    verify(spec).bind(eq("constantValueType"), eq("String"));
  }

  @Test
  void constantDetails_withPaging_bindsLimitAndOffset() {
    stubDetailSpec(Flux.empty());
    // page=3, pageSize=10 → offset = 30
    var req = new UnitListingRequest("proj", 1, null, null, null, 3, 10);
    StepVerifier.create(queries.constantDetails(req, 100)).verifyComplete();

    verify(spec).bind(eq("limit"),  eq(10));
    verify(spec).bind(eq("offset"), eq(30L));
  }

  @Test
  void constantDetails_defaultPageSize_usedWhenPageSizeAbsent() {
    stubDetailSpec(Flux.empty());
    var req = req(null, null, null);  // no pageSize → uses default 100
    StepVerifier.create(queries.constantDetails(req, 100)).verifyComplete();

    verify(spec).bind(eq("limit"),  eq(100));
    verify(spec).bind(eq("offset"), eq(0L));
  }

  // ── helpers ───────────────────────────────────────────────────────────────────

  private UnitListingRequest req(String structuralType, String semanticType, String constantValueType) {
    return new UnitListingRequest("proj", 1, structuralType, semanticType, constantValueType, null, null);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void stubCountSpec(Flux<UnitConstantsCountRow> flux) {
    RowsFetchSpec<UnitConstantsCountRow> fetchSpec = mock(RowsFetchSpec.class);
    doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
    when(fetchSpec.all()).thenReturn(flux);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void stubDetailSpec(Flux<ConstantDetailRow> flux) {
    RowsFetchSpec<ConstantDetailRow> fetchSpec = mock(RowsFetchSpec.class);
    doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
    when(fetchSpec.all()).thenReturn(flux);
  }

  private static ConstantDetailRow detailRow(String value) {
    return new ConstantDetailRow(
        value, "String", "METHOD_INVOCATION_PARAMETER",
        "LOG_MESSAGE", 0.9,
        "{\"calleeOwner\":\"com.Foo\",\"calleeName\":\"bar\",\"calleeDescriptor\":\"()V\"}",
        5L);
  }
}
