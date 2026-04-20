package org.glodean.constants.web.endpoints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
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

@WebFluxTest(controllers = ClassBinariesController.class)
@Import(InMemoryCacheTestConfig.class)
@TestPropertySource(
    properties = {
      "management.endpoints.enabled-by-default=false",
      "management.endpoint.health.enabled=false",
      "constants.solr.url=http://localhost:8983/solr/"
    })
class ClassBinariesControllerTest {
  public static final String GET_URL =
      "/class?project=demo&version=1&className=org/glodean/constants/samples/Greeter";
  public static final String PUT_URL = "/class?project=demo&version=1";
  public static final String POST_URL = "/class?project=demo";
  public static final Path SAMPLE_PATH = Path.of("src/test/resources/samples/Greeter.class");

  @Autowired WebTestClient web;

  @MockitoBean UnitConstantsStore storage;

  @Test
  void storeClass() throws IOException {
    byte[] clazz = Files.readAllBytes(SAMPLE_PATH);
    when(storage.store(any(UnitConstants.class), anyString()))
        .thenAnswer(inv -> Mono.just(inv.getArgument(0, UnitConstants.class)));
    web.post()
        .uri(POST_URL)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .bodyValue(clazz)
        .exchange()
        .expectStatus()
        .is2xxSuccessful();
  }

  @Test
  void storeBadClass() throws IOException {
    byte[] clazz = Files.readAllBytes(SAMPLE_PATH);
    clazz[0] = 'x';
    web.post()
        .uri(POST_URL)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .bodyValue(clazz)
        .exchange()
        .expectStatus()
        .is4xxClientError();
  }

  @Test
  void storeClassWithVersion() throws IOException {
    byte[] clazz = Files.readAllBytes(SAMPLE_PATH);
    when(storage.store(any(UnitConstants.class), anyString(), anyInt()))
        .thenAnswer(inv -> Mono.just(inv.getArgument(0, UnitConstants.class)));
    web.put()
        .uri(PUT_URL)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .bodyValue(clazz)
        .exchange()
        .expectStatus()
        .is2xxSuccessful();
  }

  @Test
  void classConstants() {
    when(storage.find(anyString()))
        .thenReturn(
            Mono.just(
                Map.of(
                    "1",
                    EnumSet.of(UnitConstant.UsageType.ARITHMETIC_OPERAND))));
    web.get()
        .uri(GET_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.constants")
        .isNotEmpty();
  }

  @Test
  void classConstantsNotFound() {
    when(storage.find(anyString()))
        .thenReturn(Mono.error(new IllegalArgumentException("Unknown class!")));
    web.get().uri(GET_URL).exchange().expectStatus().isNotFound();
  }

  @Test
  void classConstantsWithStorageException() {
    when(storage.find(anyString())).thenReturn(Mono.error(new RuntimeException()));
    web.get().uri(GET_URL).exchange().expectStatus().is5xxServerError();
  }
}
