package org.glodean.constants.web.endpoints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.services.NestedJarExtractionService;
import org.glodean.constants.store.UnitConstantsStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = JarBinariesController.class)
@Import(InMemoryCacheTestConfig.class)
@TestPropertySource(properties = {
    "management.endpoints.enabled-by-default=false",
    "management.endpoint.health.enabled=false",
    "constants.solr.url=http://localhost:8983/solr/"
})
class JarBinariesControllerTest {
    @Autowired WebTestClient web;

    @MockitoBean UnitConstantsStore storage;
    @MockitoBean NestedJarExtractionService nestedJarExtractionService;

    static final String POST_URL = "/jar?project=demo&jarName=test.jar";

    static UnitConstants sampleConstants() {
        var usage = new UnitConstant.ConstantUsage(
            UnitConstant.UsageType.METHOD_INVOCATION_PARAMETER,
            UnitConstant.CoreSemanticType.LOG_MESSAGE,
            new UnitConstant.UsageLocation("com/example/Greeter", "greet", "()V", 0, null),
            0.9);
        var cc = new UnitConstant("Hello", Set.of(usage));
        var descriptor = new org.glodean.constants.model.UnitDescriptor(org.glodean.constants.extractor.bytecode.BytecodeSourceKind.CLASS_FILE, "com/example/Greeter");
        return new UnitConstants(descriptor, Set.of(cc));
    }

    @Test
    void postJarSuccess() throws Exception {
        UnitConstants constants = sampleConstants();
        when(nestedJarExtractionService.extractFatJar(any(), any(), anyString()))
            .thenReturn(Flux.just(new org.glodean.constants.store.JarBatch(constants.source(), List.of(constants), true)));
        when(storage.storeAllStreaming(any(), anyString())).thenReturn(Mono.empty());

        web.post()
            .uri(POST_URL)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(new byte[]{1, 2, 3, 4})
            .exchange()
            .expectStatus().is2xxSuccessful();
    }

    @Test
    void postJarStorageExceptionStillReturns202() throws Exception {
        UnitConstants constants = sampleConstants();
        when(nestedJarExtractionService.extractFatJar(any(), any(), anyString()))
            .thenReturn(Flux.just(new org.glodean.constants.store.JarBatch(constants.source(), List.of(constants), true)));
        when(storage.storeAllStreaming(any(), anyString()))
            .thenReturn(Mono.error(new RuntimeException("DB down")));

        web.post()
            .uri(POST_URL)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(new byte[]{1, 2, 3, 4})
            .exchange()
            .expectStatus().isAccepted();
    }

    // ── validation ───────────────────────────────────────────────────────────

    @Test
    void invalidProjectNameReturns400() {
        web.post()
            .uri("/jar?project=foo:bar&jarName=test.jar")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(new byte[]{1, 2, 3, 4})
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void blankProjectNameReturns400() {
        web.post()
            .uri("/jar?project=&jarName=test.jar")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(new byte[]{1, 2, 3, 4})
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void blankJarNameReturns400() {
        web.post()
            .uri("/jar?project=demo&jarName=")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(new byte[]{1, 2, 3, 4})
            .exchange()
            .expectStatus().isBadRequest();
    }
}
