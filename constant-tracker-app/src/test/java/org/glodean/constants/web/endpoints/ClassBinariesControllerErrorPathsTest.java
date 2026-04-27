package org.glodean.constants.web.endpoints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.ModelExtractor.ExtractionException;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.services.ExtractionService;
import org.glodean.constants.services.ProjectVersionService;
import org.glodean.constants.store.UnitConstantsStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Tests for error paths in {@link ClassBinariesController} that require a mocked
 * {@link ExtractionService} (e.g., {@link ExtractionException} from the extractor).
 */
@WebFluxTest(controllers = ClassBinariesController.class)
@Import(InMemoryCacheTestConfig.class)
@TestPropertySource(
    properties = {
      "management.endpoints.enabled-by-default=false",
      "management.endpoint.health.enabled=false",
      "constants.solr.url=http://localhost:8983/solr/"
    })
class ClassBinariesControllerErrorPathsTest {

  static final String PUT_URL = "/class?project=demo&version=1";
  static final String POST_URL = "/class?project=demo";

  @Autowired WebTestClient web;

  @MockitoBean UnitConstantsStore storage;

  @MockitoBean ProjectVersionService projectVersionService;

  @MockitoBean(enforceOverride = true)
  ExtractionService extractionService;

  // ── helpers ────────────────────────────────────────────────────────────────

  static UnitConstants sampleConstants() {
    var loc = new UsageLocation("com/example/Greeter", "greet", "()V", 0, null);
    var usage = new ConstantUsage(UsageType.METHOD_INVOCATION_PARAMETER,
        CoreSemanticType.LOG_MESSAGE, loc, 0.9);
    var cc = new UnitConstant("Hello", Set.of(usage));
    var descriptor = new org.glodean.constants.model.UnitDescriptor(org.glodean.constants.extractor.bytecode.BytecodeSourceKind.CLASS_FILE, "com/example/Greeter");
    return new UnitConstants(descriptor, Set.of(cc));
  }

  // ── ExtractionException → 422 ─────────────────────────────────────────────

  @Test
  void putWithExtractionExceptionReturns422() {
    ModelExtractor mockExtractor = (src) -> {
      throw new ExtractionException("bad bytecode");
    };
    when(extractionService.extractorForClassFile(any())).thenReturn(mockExtractor);

    web.put()
        .uri(PUT_URL)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .bodyValue(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE})
        .exchange()
        .expectStatus()
        .isEqualTo(422);
  }

  @Test
  void postWithExtractionExceptionReturns422() {
    ModelExtractor mockExtractor = (src) -> {
      throw new ExtractionException("bad bytecode");
    };
    when(extractionService.extractorForClassFile(any())).thenReturn(mockExtractor);

    web.post()
        .uri(POST_URL)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .bodyValue(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE})
        .exchange()
        .expectStatus()
        .isEqualTo(422);
  }

  // ── Storage exception on PUT → 500 ────────────────────────────────────────

  @Test
  void putWithStorageExceptionReturns500() {
    UnitConstants constants = sampleConstants();
    ModelExtractor mockExtractor = (src) -> List.of(constants);
    when(extractionService.extractorForClassFile(any())).thenReturn(mockExtractor);
    when(storage.store(any(UnitConstants.class), anyString(), anyInt()))
        .thenReturn(Mono.error(new RuntimeException("DB down")));

    web.put()
        .uri(PUT_URL)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .bodyValue(new byte[]{1, 2, 3, 4})
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  // ── Storage exception on POST → 500 ───────────────────────────────────────

  @Test
  void postWithStorageExceptionReturns500() {
    UnitConstants constants = sampleConstants();
    ModelExtractor mockExtractor = (src) -> List.of(constants);
    when(extractionService.extractorForClassFile(any())).thenReturn(mockExtractor);
    when(storage.store(any(UnitConstants.class), anyString()))
        .thenReturn(Mono.error(new RuntimeException("DB down")));

    web.post()
        .uri(POST_URL)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .bodyValue(new byte[]{1, 2, 3, 4})
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  // ── Happy-path POST with mocked extractor ─────────────────────────────────

  @Test
  void postWithMockedExtractorSucceeds() {
    UnitConstants constants = sampleConstants();
    ModelExtractor mockExtractor = (src) -> List.of(constants);
    when(extractionService.extractorForClassFile(any())).thenReturn(mockExtractor);
    when(storage.store(any(UnitConstants.class), anyString()))
        .thenReturn(Mono.just(constants));

    web.post()
        .uri(POST_URL)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .bodyValue(new byte[]{1, 2, 3, 4})
        .exchange()
        .expectStatus()
        .is2xxSuccessful();
  }
}
