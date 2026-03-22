package org.glodean.constants.web.endpoints;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.util.List;
import org.glodean.constants.dto.FuzzySearchHit;
import org.glodean.constants.dto.FuzzySearchResponse;
import org.glodean.constants.store.ClassConstantsStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = FuzzySearchController.class)
@Import(InMemoryCacheTestConfig.class)
@TestPropertySource(
    properties = {
      "management.endpoints.enabled-by-default=false",
      "management.endpoint.health.enabled=false",
      "constants.solr.url=http://localhost:8983/solr/"
    })
class FuzzySearchControllerTest {

  @Autowired WebTestClient web;

  @MockitoBean ClassConstantsStore store;

  // ── happy path ──────────────────────────────────────────────────────────

  @Test
  void searchReturnsHits() {
    FuzzySearchResponse response =
        new FuzzySearchResponse(
            List.of(
                new FuzzySearchHit(
                    "my-app", "org/example/Repo", 1, List.of("SELECT * FROM users"))),
            1L);
    // default fuzzy=1, rows=10
    when(store.fuzzySearch(eq("my-app"), eq("SELECT"), eq(1), eq(10)))
        .thenReturn(Mono.just(response));

    web.get()
        .uri("/search?project=my-app&term=SELECT")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.totalFound")
        .isEqualTo(1)
        .jsonPath("$.hits[0].className")
        .isEqualTo("org/example/Repo")
        .jsonPath("$.hits[0].constantValues[0]")
        .isEqualTo("SELECT * FROM users");
  }

  @Test
  void exactSearchPassesEditDistanceZero() {
    when(store.fuzzySearch(eq("my-app"), eq("SELECT"), eq(0), eq(10)))
        .thenReturn(Mono.just(new FuzzySearchResponse(List.of(), 0L)));

    web.get()
        .uri("/search?project=my-app&term=SELECT&fuzzy=0")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.totalFound")
        .isEqualTo(0);
  }

  @Test
  void broadFuzzySearchPassesEditDistanceTwo() {
    when(store.fuzzySearch(eq("my-app"), eq("SELCT"), eq(2), eq(10)))
        .thenReturn(Mono.just(new FuzzySearchResponse(List.of(), 0L)));

    web.get()
        .uri("/search?project=my-app&term=SELCT&fuzzy=2")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void searchReturnsEmptyResponse() {
    when(store.fuzzySearch(anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(Mono.just(new FuzzySearchResponse(List.of(), 0L)));

    web.get()
        .uri("/search?project=my-app&term=nonexistent")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.totalFound")
        .isEqualTo(0)
        .jsonPath("$.hits")
        .isEmpty();
  }

  // ── capping rows ─────────────────────────────────────────────────────────

  @Test
  void rowsAreCappedAt100() {
    when(store.fuzzySearch(eq("p"), eq("q"), eq(1), eq(100)))
        .thenReturn(Mono.just(new FuzzySearchResponse(List.of(), 0L)));

    // request 999 – should be silently capped to 100
    web.get().uri("/search?project=p&term=q&rows=999").exchange().expectStatus().isOk();
  }

  @Test
  void rowsFloorIsOne() {
    when(store.fuzzySearch(eq("p"), eq("q"), eq(1), eq(1)))
        .thenReturn(Mono.just(new FuzzySearchResponse(List.of(), 0L)));

    web.get().uri("/search?project=p&term=q&rows=0").exchange().expectStatus().isOk();
  }

  // ── input validation ──────────────────────────────────────────────────────

  @Test
  void invalidFuzzyValueReturns400() {
    web.get()
        .uri("/search?project=my-app&term=SELECT&fuzzy=3")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void negativeFuzzyValueReturns400() {
    web.get()
        .uri("/search?project=my-app&term=SELECT&fuzzy=-1")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  // ── store failure ─────────────────────────────────────────────────────────

  @Test
  void storeExceptionReturns500() {
    when(store.fuzzySearch(anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(Mono.error(new RuntimeException("Solr down")));

    web.get()
        .uri("/search?project=my-app&term=SELECT")
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  // ── blank parameter validation ────────────────────────────────────────────

  @Test
  void blankProjectReturns400() {
    web.get()
        .uri("/search?project=&term=SELECT")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void blankTermReturns400() {
    web.get()
        .uri("/search?project=my-app&term=")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }
}
