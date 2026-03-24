package org.glodean.constants.web.endpoints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstants;
import org.glodean.constants.services.ExtractionService;
import org.glodean.constants.store.ClassConstantsStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
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

    @MockitoBean ClassConstantsStore storage;
    @MockitoBean ExtractionService extractionService;

    static final String POST_URL = "/jar?project=demo";

    static ClassConstants sampleConstants() {
        var usage = new ClassConstant.ConstantUsage(
            ClassConstant.UsageType.METHOD_INVOCATION_PARAMETER,
            ClassConstant.CoreSemanticType.LOG_MESSAGE,
            new ClassConstant.UsageLocation("com/example/Greeter", "greet", "()V", 0, null),
            0.9);
        var cc = new ClassConstant("Hello", Set.of(usage));
        return new ClassConstants("com/example/Greeter", Set.of(cc));
    }

    @Test
    void postJarSuccess() {
        ClassConstants constants = sampleConstants();
        ModelExtractor mockExtractor = () -> List.of(constants);
        when(extractionService.extractorForJarFile(any())).thenReturn(mockExtractor);
        when(storage.store(any(ClassConstants.class), anyString())).thenReturn(Mono.just(constants));

        web.post()
            .uri(POST_URL)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(new byte[]{1, 2, 3, 4})
            .exchange()
            .expectStatus().is2xxSuccessful();
    }

    @Test
    void postJarExtractionExceptionReturns422() {
        ModelExtractor mockExtractor = () -> { throw new ModelExtractor.ExtractionException("bad jar"); };
        when(extractionService.extractorForJarFile(any())).thenReturn(mockExtractor);

        web.post()
            .uri(POST_URL)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(new byte[]{1, 2, 3, 4})
            .exchange()
            .expectStatus().isEqualTo(422);
    }

    @Test
    void postJarIllegalArgumentReturns422() {
        when(extractionService.extractorForJarFile(any())).thenThrow(new IllegalArgumentException("bad arg"));

        web.post()
            .uri(POST_URL)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(new byte[]{1, 2, 3, 4})
            .exchange()
            .expectStatus().isEqualTo(422);
    }

    @Test
    void postJarStorageExceptionReturns500() {
        ClassConstants constants = sampleConstants();
        ModelExtractor mockExtractor = () -> List.of(constants);
        when(extractionService.extractorForJarFile(any())).thenReturn(mockExtractor);
        when(storage.store(any(ClassConstants.class), anyString())).thenReturn(Mono.error(new RuntimeException("DB down")));

        web.post()
            .uri(POST_URL)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(new byte[]{1, 2, 3, 4})
            .exchange()
            .expectStatus().is5xxServerError();
    }
}

