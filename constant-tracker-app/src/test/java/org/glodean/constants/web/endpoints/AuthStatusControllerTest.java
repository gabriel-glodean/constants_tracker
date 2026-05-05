package org.glodean.constants.web.endpoints;
import static org.mockito.Mockito.*;
import org.glodean.constants.auth.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
/** Slice tests for {@link AuthStatusController}. */
@WebFluxTest(controllers = AuthStatusController.class)
@Import(AuthStatusControllerTest.OpenSecurity.class)
class AuthStatusControllerTest {
  @TestConfiguration
  static class OpenSecurity {
    @Bean
    SecurityWebFilterChain chain(ServerHttpSecurity http) {
      http.csrf(ServerHttpSecurity.CsrfSpec::disable);
      http.authorizeExchange(ex -> ex.anyExchange().permitAll());
      return http.build();
    }
  }
  @Autowired WebTestClient web;
  @MockitoBean AuthProperties authProperties;
  @Test
  void authStatus_returnsEnabledTrue() {
    when(authProperties.enabled()).thenReturn(true);
    web.get()
        .uri("/auth/status")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.enabled").isEqualTo(true);
  }
  @Test
  void authStatus_returnsEnabledFalse() {
    when(authProperties.enabled()).thenReturn(false);
    web.get()
        .uri("/auth/status")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.enabled").isEqualTo(false);
  }
}
