package org.glodean.constants.web.endpoints;

import static org.mockito.Mockito.when;

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

  @Test
  void listUnits_groupsRowsByDescriptorPath() {
    when(unitSnapshotRepository.findUnitConstantCountsByProjectAndVersion("demo", 2))
        .thenReturn(Flux.just(
            row("app.jar", "com/acme/A.class", 4L),
            row("app.jar", "application.yml", 2L),
            row("lib.jar", "com/lib/B.class", 1L)));

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
  void listUnits_requiresVersion() {
    web.get()
        .uri("/units?project=demo")
        .exchange()
        .expectStatus().isBadRequest();
  }

  private static UnitConstantsCountRow row(String unitPath, String name, Long constants) {
    return new UnitConstantsCountRow() {
      @Override
      public String getUnitPath() {
        return unitPath;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public Long getConstants() {
        return constants;
      }
    };
  }
}

