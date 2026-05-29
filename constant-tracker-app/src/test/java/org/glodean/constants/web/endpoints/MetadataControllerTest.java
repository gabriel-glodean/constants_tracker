package org.glodean.constants.web.endpoints;

import static org.mockito.Mockito.when;

import java.util.Set;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.CustomSemanticType;
import org.glodean.constants.store.SemanticTypeStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = MetadataController.class)
@Import(InMemoryCacheTestConfig.class)
@TestPropertySource(properties = {
    "management.endpoints.enabled-by-default=false",
    "management.endpoint.health.enabled=false",
    "constants.solr.url=http://localhost:8983/solr/"
})
class MetadataControllerTest {

  @Autowired WebTestClient web;


  @MockitoBean SemanticTypeStore semanticTypeStore;

    @Test
    void metadataReturnsTypesUsageTypesAndSemanticTypes() {
      when(semanticTypeStore.getSupportedSemanticTypes()).thenReturn(Set.of(
          CoreSemanticType.LOG_MESSAGE,
          new CustomSemanticType("team.audit", "Team Audit", "Audit event marker")));

      web.get()
          .uri("/metadata")
          .exchange()
          .expectStatus().isOk()
          .expectBody()
          .jsonPath("$.types[0].name").isEqualTo("ArrayDesc")
          .jsonPath("$.types[1].name").isEqualTo("Boolean")
          .jsonPath("$.types[4].name").isEqualTo("ClassDescriptor")
          .jsonPath("$.types[6].name").isEqualTo("DynamicConstant")
          .jsonPath("$.types[9].name").isEqualTo("Long")
          .jsonPath("$.types[9].displayName").isEqualTo("Long")
          .jsonPath("$.structuralTypes[5].name").isEqualTo("METHOD_INVOCATION_PARAMETER")
          .jsonPath("$.structuralTypes[5].displayName").isEqualTo("Method Invocation Parameter")
          .jsonPath("$.semanticTypes[0].name").isEqualTo("LOG_MESSAGE")
          .jsonPath("$.semanticTypes[0].displayName").isEqualTo("Log Message")
          .jsonPath("$.semanticTypes[1].name").isEqualTo("team.audit")
          .jsonPath("$.semanticTypes[1].displayName").isEqualTo("Team Audit");
    }

   @Test
   void semanticTypesEndpointReturnsStoreMetadata() {
     when(semanticTypeStore.getSupportedSemanticTypes()).thenReturn(Set.of(
         CoreSemanticType.URL_RESOURCE,
         new CustomSemanticType("aws", "AWS ARN", "Amazon resource name")));

     web.get()
         .uri("/metadata/semantic-types")
         .exchange()
         .expectStatus().isOk()
         .expectBody()
         .jsonPath("$[0].name").isEqualTo("aws")
         .jsonPath("$[0].displayName").isEqualTo("AWS ARN")
         .jsonPath("$[1].name").isEqualTo("URL_RESOURCE")
         .jsonPath("$[1].displayName").isEqualTo("URL Resource");
   }

    @Test
    void typesEndpointReturnsConstantValueTypes() {
      when(semanticTypeStore.getSupportedSemanticTypes()).thenReturn(Set.of(UnitConstant.CoreSemanticType.UNKNOWN));

      web.get()
          .uri("/metadata/types")
          .exchange()
          .expectStatus().isOk()
          .expectBody()
          .jsonPath("$[0].name").isEqualTo("ArrayDesc")
          .jsonPath("$[1].name").isEqualTo("Boolean")
          .jsonPath("$[2].name").isEqualTo("Byte")
          .jsonPath("$[4].name").isEqualTo("ClassDescriptor")
          .jsonPath("$[6].name").isEqualTo("DynamicConstant")
          .jsonPath("$[10].name").isEqualTo("MethodHandle")
          .jsonPath("$[11].name").isEqualTo("Null")
          .jsonPath("$[13].name").isEqualTo("String");
    }
}
