package org.glodean.constants.web.endpoints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.glodean.constants.extractor.configfile.ConfigFileSourceKind;
import org.glodean.constants.extractor.configfile.PropertiesConstantsExtractor;
import org.glodean.constants.extractor.configfile.YamlConstantsExtractor;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.store.UnitConstantsStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = ConfigFileController.class)
@Import({InMemoryCacheTestConfig.class, ConfigFileControllerTest.ConfigFileExtractorConfig.class})
@TestPropertySource(properties = {
    "management.endpoints.enabled-by-default=false",
    "management.endpoint.health.enabled=false",
    "constants.solr.url=http://localhost:8983/solr/"
})
class ConfigFileControllerTest {

    @Autowired WebTestClient web;

    @MockitoBean UnitConstantsStore storage;

    /** Provides real extractor beans for the controller. */
    @org.springframework.boot.test.context.TestConfiguration
    static class ConfigFileExtractorConfig {
        @Bean
        YamlConstantsExtractor yamlConstantsExtractor() {
            return new YamlConstantsExtractor();
        }

        @Bean
        PropertiesConstantsExtractor propertiesConstantsExtractor() {
            return new PropertiesConstantsExtractor();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    static UnitConstants sampleConstants() {
        var loc = new UsageLocation("application.yml", "server.port", "doc#0", null, 0);
        var usage = new ConstantUsage(UsageType.FIELD_STORE, CoreSemanticType.CONFIGURATION_VALUE, loc, 0.9);
        var cc = new UnitConstant(8080, Set.of(usage));
        var descriptor = new UnitDescriptor(ConfigFileSourceKind.YAML, "application.yml");
        return new UnitConstants(descriptor, Set.of(cc));
    }

    private MultipartBodyBuilder yamlMultipart() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("samples/application.yml"))
                .filename("application.yml");
        return builder;
    }

    private MultipartBodyBuilder propertiesMultipart() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("samples/application.properties"))
                .filename("application.properties");
        return builder;
    }

    private MultipartBodyBuilder unsupportedMultipart() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", "some text content")
                .filename("readme.md");
        return builder;
    }

    // ── POST (auto-version) ─────────────────────────────────────────────────

    @Test
    void postYamlSuccess() {
        when(storage.store(any(UnitConstants.class), anyString()))
                .thenReturn(Mono.just(sampleConstants()));

        web.post()
                .uri("/config?project=demo")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(yamlMultipart().build()))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void postPropertiesSuccess() {
        when(storage.store(any(UnitConstants.class), anyString()))
                .thenReturn(Mono.just(sampleConstants()));

        web.post()
                .uri("/config?project=demo")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(propertiesMultipart().build()))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void postUnsupportedFileReturns422() {
        web.post()
                .uri("/config?project=demo")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(unsupportedMultipart().build()))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    // ── PUT (explicit version) ──────────────────────────────────────────────

    @Test
    void putYamlWithVersionSuccess() {
        when(storage.store(any(UnitConstants.class), anyString(), anyInt()))
                .thenReturn(Mono.just(sampleConstants()));

        web.put()
                .uri("/config?project=demo&version=1")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(yamlMultipart().build()))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void putUnsupportedFileReturns422() {
        web.put()
                .uri("/config?project=demo&version=1")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(unsupportedMultipart().build()))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    // ── Storage errors → 500 ────────────────────────────────────────────────

    @Test
    void postStorageErrorReturns500() {
        when(storage.store(any(UnitConstants.class), anyString()))
                .thenReturn(Mono.error(new RuntimeException("DB down")));

        web.post()
                .uri("/config?project=demo")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(yamlMultipart().build()))
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void putStorageErrorReturns500() {
        when(storage.store(any(UnitConstants.class), anyString(), anyInt()))
                .thenReturn(Mono.error(new RuntimeException("DB down")));

        web.put()
                .uri("/config?project=demo&version=1")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(yamlMultipart().build()))
                .exchange()
                .expectStatus().is5xxServerError();
    }
}

