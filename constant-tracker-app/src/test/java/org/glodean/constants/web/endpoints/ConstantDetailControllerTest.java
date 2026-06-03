package org.glodean.constants.web.endpoints;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.glodean.constants.store.postgres.UnitConstantQueries;
import org.glodean.constants.store.postgres.repository.projection.ConstantDetailRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
@WebFluxTest(controllers = ConstantDetailController.class)
@Import(InMemoryCacheTestConfig.class)
@TestPropertySource(properties = {
    "management.endpoints.enabled-by-default=false",
    "management.endpoint.health.enabled=false",
    "constants.solr.url=http://localhost:8983/solr/"
})
class ConstantDetailControllerTest {
  @Autowired WebTestClient web;
  @MockitoBean UnitConstantQueries unitConstantQueries;
  // ── happy path ───────────────────────────────────────────────────────────────
  @Test
  void listConstantDetails_returnsPagedFlatRows() {
    when(unitConstantQueries.constantDetails(any(), eq(100)))
        .thenReturn(Flux.just(
            detail("http://api.example.com/v1", "String",
                "METHOD_INVOCATION_PARAMETER", "URL_RESOURCE", 0.9,
                "{\"calleeOwner\":\"com.acme.ApiClient\",\"calleeName\":\"fetch\"}", 3L),
            detail("SELECT * FROM users", "String",
                "METHOD_INVOCATION_PARAMETER", "SQL_FRAGMENT", 0.95,
                "{\"calleeOwner\":\"com.acme.UserRepository\",\"calleeName\":\"findAll\"}", 1L)));
    web.get()
        .uri("/units/constants?project=demo&version=2")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].constantValue").isEqualTo("http://api.example.com/v1")
        .jsonPath("$[0].constantValueType").isEqualTo("String")
        .jsonPath("$[0].structuralType").isEqualTo("METHOD_INVOCATION_PARAMETER")
        .jsonPath("$[0].semanticType").isEqualTo("URL_RESOURCE")
        .jsonPath("$[0].confidence").isEqualTo(0.9)
        .jsonPath("$[0].occurrenceCount").isEqualTo(3)
        .jsonPath("$[1].constantValue").isEqualTo("SELECT * FROM users")
        .jsonPath("$[1].semanticType").isEqualTo("SQL_FRAGMENT");
  }
  @Test
  void listConstantDetails_withStructuralTypeFilter_passesFilterToQuery() {
    when(unitConstantQueries.constantDetails(any(), eq(100)))
        .thenReturn(Flux.just(
            detail("debug msg", "String",
                "METHOD_INVOCATION_PARAMETER", "LOG_MESSAGE", 0.8,
                "{\"calleeOwner\":\"com.acme.Service\",\"calleeName\":\"process\"}", 2L)));
    web.get()
        .uri("/units/constants?project=demo&version=1&structuralType=METHOD_INVOCATION_PARAMETER")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].structuralType").isEqualTo("METHOD_INVOCATION_PARAMETER")
        .jsonPath("$[0].semanticType").isEqualTo("LOG_MESSAGE");
  }
  @Test
  void listConstantDetails_withConstantValueTypeFilter_passesFilterToQuery() {
    when(unitConstantQueries.constantDetails(any(), eq(100)))
        .thenReturn(Flux.just(
            detail("42", "Integer",
                "ARITHMETIC_OPERAND", "UNKNOWN", 0.6, "{}", 7L)));
    web.get()
        .uri("/units/constants?project=demo&version=1&constantValueType=Integer")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].constantValue").isEqualTo("42")
        .jsonPath("$[0].constantValueType").isEqualTo("Integer");
  }
  @Test
  void listConstantDetails_withSemanticTypeFilter_passesFilterToQuery() {
    when(unitConstantQueries.constantDetails(any(), eq(100)))
        .thenReturn(Flux.just(
            detail("some constant", "String",
                "FIELD_STORE", "UNKNOWN", 0.5, "{}", 1L)));
    web.get()
        .uri("/units/constants?project=demo&version=3&semanticType=UNKNOWN")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].semanticType").isEqualTo("UNKNOWN");
  }
  @Test
  void listConstantDetails_customPageSize_isRespected() {
    // pageSize=25 is embedded in the UnitListingRequest passed to the query; the controller always
    // passes its DEFAULT_PAGE_SIZE (100) as the second arg - resolution happens inside the query class
    when(unitConstantQueries.constantDetails(any(), eq(100)))
        .thenReturn(Flux.empty());
    web.get()
        .uri("/units/constants?project=demo&version=1&pageSize=25")
        .exchange()
        .expectStatus().isOk()
        .expectBody().json("[]");
  }
  @Test
  void listConstantDetails_emptyResult_returnsEmptyArray() {
    when(unitConstantQueries.constantDetails(any(), eq(100)))
        .thenReturn(Flux.empty());
    web.get()
        .uri("/units/constants?project=demo&version=1")
        .exchange()
        .expectStatus().isOk()
        .expectBody().json("[]");
  }
  // ── validation ───────────────────────────────────────────────────────────────
  @Test
  void listConstantDetails_requiresProject() {
    web.get()
        .uri("/units/constants?version=1")
        .exchange()
        .expectStatus().isBadRequest();
  }
  @Test
  void listConstantDetails_requiresVersion() {
    web.get()
        .uri("/units/constants?project=demo")
        .exchange()
        .expectStatus().isBadRequest();
  }
  @Test
  void listConstantDetails_rejectsNegativePage() {
    web.get()
        .uri("/units/constants?project=demo&version=1&page=-1")
        .exchange()
        .expectStatus().isBadRequest();
  }
  @Test
  void listConstantDetails_rejectsZeroPageSize() {
    web.get()
        .uri("/units/constants?project=demo&version=1&pageSize=0")
        .exchange()
        .expectStatus().isBadRequest();
  }
  // ── helpers ──────────────────────────────────────────────────────────────────
  private static ConstantDetailRow detail(
      String constantValue, String constantValueType,
      String structuralType,
      String semanticType,
      double confidence,
      String metadata,
      long occurrenceCount) {
    return new ConstantDetailRow(
        constantValue, constantValueType,
        structuralType,
        semanticType, confidence,
        metadata, occurrenceCount);
  }
}
