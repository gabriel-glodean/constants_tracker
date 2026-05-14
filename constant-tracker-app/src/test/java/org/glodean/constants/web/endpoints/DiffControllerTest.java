package org.glodean.constants.web.endpoints;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import org.glodean.constants.dto.ProjectDiffResponse;
import org.glodean.constants.dto.UnitDiff;
import org.glodean.constants.services.DiffService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = DiffController.class)
@Import(InMemoryCacheTestConfig.class)
@TestPropertySource(properties = {
    "management.endpoints.enabled-by-default=false",
    "management.endpoint.health.enabled=false",
    "constants.solr.url=http://localhost:8983/solr/"
})
class DiffControllerTest {

    @Autowired WebTestClient web;

    @MockitoBean DiffService diffService;

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    void diffReturns200() {
        var response = new ProjectDiffResponse("demo", 1, 2, List.of(
                new UnitDiff("com.example.Foo", false, false, List.of())
        ));
        when(diffService.diff("demo", 1, 2)).thenReturn(Mono.just(response));

        web.get()
                .uri("/project/demo/diff?from=1&to=2")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.units[0].path").isEqualTo("com.example.Foo");
    }

    @Test
    void emptyDiffReturns200() {
        when(diffService.diff(anyString(), anyInt(), anyInt()))
                .thenReturn(Mono.just(new ProjectDiffResponse("demo", 1, 2, List.of())));

        web.get()
                .uri("/project/demo/diff?from=1&to=2")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.units").isEmpty();
    }

    @Test
    void storeExceptionReturns500() {
        when(diffService.diff(anyString(), anyInt(), anyInt()))
                .thenReturn(Mono.error(new RuntimeException("DB down")));

        web.get()
                .uri("/project/demo/diff?from=1&to=2")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    // ── validation ───────────────────────────────────────────────────────────

    @Test
    void invalidProjectReturns400() {
        web.get()
                .uri("/project/foo:bar/diff?from=1&to=2")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void blankProjectReturns400() {
        // blank path segment isn't routable; Spring returns 404 before validation
        // so we test a project with invalid chars instead
        web.get()
                .uri("/project/foo+bar/diff?from=1&to=2")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void zeroFromVersionReturns400() {
        web.get()
                .uri("/project/demo/diff?from=0&to=2")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void negativeToVersionReturns400() {
        web.get()
                .uri("/project/demo/diff?from=1&to=-1")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
