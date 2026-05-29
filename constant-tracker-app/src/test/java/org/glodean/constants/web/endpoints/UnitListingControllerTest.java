package org.glodean.constants.web.endpoints;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.glodean.constants.store.postgres.UnitConstantQueries;
import org.glodean.constants.store.postgres.repository.UnitSnapshotRepository;
import org.glodean.constants.store.postgres.repository.projection.UnitConstantsCountRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
@WebFluxTest(controllers = UnitListingController.class)
@Import(InMemoryCacheTestConfig.class)
@TestPropertySource(properties = {
    "management.endpoints.enabled-by-default=false",
    "management.endpoint.health.enabled=false",
    "constants.solr.url=http://localhost:8983/solr/"
})
class UnitListingControllerTest {
  @Autowired WebTestClient web;
  @MockitoBean UnitSnapshotRepository unitSnapshotRepository;
  @MockitoBean UnitConstantQueries    unitConstantQueries;
  // ── unfiltered path ─────────────────────────────────────────────────────────
  @Test
  void listUnits_groupsRowsByDescriptorPath() {
    when(unitSnapshotRepository.findUnitConstantCountsByProjectAndVersion("demo", 2))
        .thenReturn(Flux.just(
            row("app.jar", "com/acme/A.class", 4L),
            row("app.jar", "application.yml",  2L),
            row("lib.jar", "com/lib/B.class",  1L)));
    web.get()
        .uri("/units?project=demo&version=2")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].unitPath").isEqualTo("app.jar")
        .jsonPath("$[0].units[0].name").isEqualTo("com/acme/A.class")
        .jsonPath("$[0].units[0].constants").isEqualTo(4)
        .jsonPath("$[0].units[1].name").isEqualTo("application.yml")
        .jsonPath("$[0].units[1].constants").isEqualTo(2)
        .jsonPath("$[1].unitPath").isEqualTo("lib.jar")
        .jsonPath("$[1].units[0].name").isEqualTo("com/lib/B.class")
        .jsonPath("$[1].units[0].constants").isEqualTo(1);
  }
  @Test
  void listUnits_stripsLeadingWhitespaceFromProject() {
    when(unitSnapshotRepository.findUnitConstantCountsByProjectAndVersion("demo", 1))
        .thenReturn(Flux.just(row("a.jar", "A.class", 2L)));
    web.get()
        .uri("/units?project=demo&version=1")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].unitPath").isEqualTo("a.jar");
  }
  // ── filtered path ────────────────────────────────────────────────────────────
  @Test
  void listUnits_routesToFilteredQuery_whenStructuralTypeSupplied() {
    when(unitConstantQueries.unitCounts(any(), eq(50)))
        .thenReturn(Flux.just(row("app.jar", "com/acme/A.class", 3L)));
    web.get()
        .uri("/units?project=demo&version=2&structuralType=METHOD_INVOCATION_PARAMETER")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].unitPath").isEqualTo("app.jar")
        .jsonPath("$[0].units[0].constants").isEqualTo(3);
  }
  @Test
  void listUnits_routesToFilteredQuery_whenSemanticTypeSupplied() {
    when(unitConstantQueries.unitCounts(any(), eq(50)))
        .thenReturn(Flux.just(row("lib.jar", "com/lib/B.class", 7L)));
    web.get()
        .uri("/units?project=demo&version=2&semanticType=UNKNOWN")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].unitPath").isEqualTo("lib.jar")
        .jsonPath("$[0].units[0].constants").isEqualTo(7);
  }
  @Test
  void listUnits_filteredWithPaging_returnsPage() {
    when(unitConstantQueries.unitCounts(any(), eq(50)))
        .thenReturn(Flux.just(row("app.jar", "com/acme/A.class", 1L)));
    web.get()
        .uri("/units?project=demo&version=2&semanticType=LOG_MESSAGE&page=1&pageSize=10")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].unitPath").isEqualTo("app.jar");
  }
  @Test
  void listUnits_routesToFilteredQuery_whenConstantValueTypeSupplied() {
    when(unitConstantQueries.unitCounts(any(), eq(50)))
        .thenReturn(Flux.just(row("lib.jar", "com/lib/C.class", 2L)));
    web.get()
        .uri("/units?project=demo&version=2&constantValueType=String")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].unitPath").isEqualTo("lib.jar")
        .jsonPath("$[0].units[0].constants").isEqualTo(2);
  }
  // ── validation ───────────────────────────────────────────────────────────────
  @Test
  void listUnits_requiresVersion() {
    web.get()
        .uri("/units?project=demo")
        .exchange()
        .expectStatus().isBadRequest();
  }
  @Test
  void listUnits_requiresProject() {
    web.get()
        .uri("/units?version=1")
        .exchange()
        .expectStatus().isBadRequest();
  }
  @Test
  void listUnits_rejectsNegativePage() {
    web.get()
        .uri("/units?project=demo&version=1&semanticType=UNKNOWN&page=-1")
        .exchange()
        .expectStatus().isBadRequest();
  }
  @Test
  void listUnits_rejectsZeroPageSize() {
    web.get()
        .uri("/units?project=demo&version=1&semanticType=UNKNOWN&pageSize=0")
        .exchange()
        .expectStatus().isBadRequest();
  }
  // ── helpers ──────────────────────────────────────────────────────────────────
  private static UnitConstantsCountRow row(String path, String name, Long constants) {
    return new UnitConstantsCountRow(path, name, constants);
  }
}
