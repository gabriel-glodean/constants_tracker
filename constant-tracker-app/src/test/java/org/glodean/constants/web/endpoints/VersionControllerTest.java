package org.glodean.constants.web.endpoints;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.glodean.constants.services.ProjectVersionService;
import org.glodean.constants.store.postgres.ProjectVersionEntity;
import org.glodean.constants.store.postgres.UnitDescriptorEntity;
import org.glodean.constants.store.postgres.UnitDescriptorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = VersionController.class)
@Import(InMemoryCacheTestConfig.class)
@TestPropertySource(
    properties = {
      "management.endpoints.enabled-by-default=false",
      "management.endpoint.health.enabled=false",
      "constants.solr.url=http://localhost:8983/solr/"
    })
class VersionControllerTest {

  @Autowired WebTestClient web;

  @MockitoBean ProjectVersionService projectVersionService;

  @MockitoBean UnitDescriptorRepository descriptorRepo;

  @Test
  void finalizeVersionReturns200() {
    var entity =
        new ProjectVersionEntity(
            1L, "demo", 1, null, "FINALIZED", LocalDateTime.now(), LocalDateTime.now());
    when(projectVersionService.finalizeVersion("demo", 1)).thenReturn(Mono.just(entity));

    web.post()
        .uri("/project/demo/version/1/finalize")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("FINALIZED");
  }

  @Test
  void finalizeVersionReturns404WhenNotFound() {
    when(projectVersionService.finalizeVersion("demo", 99))
        .thenReturn(Mono.error(new IllegalArgumentException("not found")));

    web.post()
        .uri("/project/demo/version/99/finalize")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void finalizeVersionReturns409WhenAlreadyFinalized() {
    when(projectVersionService.finalizeVersion("demo", 1))
        .thenReturn(Mono.error(new IllegalStateException("already finalized")));

    web.post()
        .uri("/project/demo/version/1/finalize")
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }

  @Test
  void getVersionReturns200() {
    var entity =
        new ProjectVersionEntity(
            1L, "demo", 1, null, "OPEN", LocalDateTime.now(), null);
    when(projectVersionService.getVersion("demo", 1)).thenReturn(Mono.just(entity));

    web.get()
        .uri("/project/demo/version/1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.version")
        .isEqualTo(1)
        .jsonPath("$.status")
        .isEqualTo("OPEN");
  }

  @Test
  void getVersionReturns404WhenNotFound() {
    when(projectVersionService.getVersion("demo", 99)).thenReturn(Mono.empty());

    web.get().uri("/project/demo/version/99").exchange().expectStatus().isNotFound();
  }

  @Test
  void syncRemovalsReturns200WithRemovedPaths() {
    when(descriptorRepo.findAllByProjectAndVersion("demo", 2))
        .thenReturn(
            Flux.just(
                new UnitDescriptorEntity(
                    1L, "demo", 2, "CLASS_FILE", "com/ClassA", 100, null)));
    when(projectVersionService.recordRemovals(anyString(), anyInt(), anySet()))
        .thenReturn(Flux.just("com/ClassB"));

    web.post()
        .uri("/project/demo/version/2/sync")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0]")
        .isEqualTo("com/ClassB");
  }

  // Helper for Set matcher
  private static java.util.Set<String> anySet() {
    return org.mockito.ArgumentMatchers.anySet();
  }
}

