package org.glodean.constants.web.endpoints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.apache.solr.common.util.NamedList;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.store.SolrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = ClassBinariesController.class)
@Import(InMemoryCacheTestConfig.class)
@TestPropertySource(
    properties = {
      "management.endpoints.enabled-by-default=false",
      "management.endpoint.health.enabled=false"
    })
class ClassBinariesControllerTest {
  public static final String GET_URL =
      "/class?project=demo&version=1&className=org/glodean/constants/samples/Greeter";
  public static final String PUT_URL = "/class?project=demo&version=1";
  public static final String POST_URL = "/class?project=demo";
  public static final Path SAMPLE_PATH = Path.of("src/test/resources/samples/Greeter.class");
  @Autowired WebTestClient web;

  @MockitoBean RedisConnectionFactory redisConnectionFactory;

  @MockitoBean RedisConnection redisConnection;

  @MockitoBean HttpSolrClientBase solrClient;

  @Autowired CacheManager cacheManager;

  Map<String, AtomicLong> store;

  @BeforeEach
  void setup() {
    store = new ConcurrentHashMap<>();
    Objects.requireNonNull(cacheManager.getCache(SolrService.DATA_LOCATION)).clear();
    when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
  }

  @Test
  void storeClass() throws IOException {
    byte[] clazz = Files.readAllBytes(SAMPLE_PATH);
    when(solrClient.requestAsync(any(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(new NamedList<>()));
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
    when(solrClient.requestAsync(any(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(new NamedList<>()));
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
    when(solrClient.requestAsync(any(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(new NamedList<>()));
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
    var response = new NamedList<>();
    response.add(
        "response",
        List.of(
            Map.of(
                "constant_value_s",
                "1",
                "usage_type_s",
                ClassConstant.UsageType.PROPAGATION_IN_ARITHMETIC_OPERATIONS.name())));
    when(solrClient.requestAsync(any(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(response));
    // first call, not cached
    web.get()
        .uri(GET_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.constants")
        .isNotEmpty();
    verify(solrClient, only()).requestAsync(any(), anyString());

    // second call, cached
    web.get()
        .uri(GET_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.constants")
        .isNotEmpty();
    verify(solrClient, only()).requestAsync(any(), anyString());
  }

  @Test
  void classConstantsNotFound() {
    var response = new NamedList<>();
    response.add("response", List.of());
    when(solrClient.requestAsync(any(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(response));
    web.get().uri(GET_URL).exchange().expectStatus().isNotFound();
  }

  @Test
  void classConstantsWithSolrException() {
    when(solrClient.requestAsync(any(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new IOException()));
    web.get().uri(GET_URL).exchange().expectStatus().is5xxServerError();
  }
}
