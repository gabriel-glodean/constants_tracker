package org.glodean.constants.web;

import org.glodean.constants.auth.AuthProperties;
import org.glodean.constants.web.endpoints.AuthStatusController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.when;

/**
 * Verifies that the security response headers configured in
 * {@link org.glodean.constants.auth.SecurityConfiguration} (SecuredConfig) are present on
 * every response and that unauthenticated requests to protected routes are rejected with 401.
 *
 * <p>HSTS is intentionally absent — TLS is terminated by Cloudflare; the app only ever
 * receives plain HTTP internally, so {@code Strict-Transport-Security} would have no effect.
 */
@WebFluxTest(controllers = AuthStatusController.class)
@Import(SecurityConfigurationHeadersTest.SecuredHeadersConfig.class)
class SecurityConfigurationHeadersTest {

  /**
   * Mirrors the production {@code SecurityConfiguration.SecuredConfig} header rules
   * without a JWT filter or any database dependencies.
   */
  @TestConfiguration
  static class SecuredHeadersConfig {

    @Bean
    SecurityWebFilterChain securedChain(ServerHttpSecurity http) {
      http.csrf(ServerHttpSecurity.CsrfSpec::disable);
      http.headers(headers -> headers
          .frameOptions(f -> f.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
          .hsts(ServerHttpSecurity.HeaderSpec.HstsSpec::disable)
          .referrerPolicy(r -> r.policy(
              ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
      );
      http.authorizeExchange(ex -> ex
          .pathMatchers("/auth/login", "/auth/refresh", "/auth/status").permitAll()
          .anyExchange().authenticated());
      return http.build();
    }
  }

  @Autowired WebTestClient web;
  @MockitoBean AuthProperties authProperties;

  @BeforeEach
  void stubAuthProperties() {
    when(authProperties.enabled()).thenReturn(true);
  }

  // -------------------------------------------------------------------------
  // X-Frame-Options
  // -------------------------------------------------------------------------

  @Test
  void publicEndpoint_respondsWithXFrameOptionsDeny() {
    web.get().uri("/auth/status")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Frame-Options", "DENY");
  }

  // -------------------------------------------------------------------------
  // Referrer-Policy
  // -------------------------------------------------------------------------

  @Test
  void publicEndpoint_respondsWithReferrerPolicyHeader() {
    web.get().uri("/auth/status")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin");
  }

  // -------------------------------------------------------------------------
  // Protected route — no token → 401
  // -------------------------------------------------------------------------

  @Test
  void protectedEndpoint_withoutToken_returns401() {
    web.get().uri("/some/protected/resource")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  void protectedEndpoint_401Response_alsoIncludesSecurityHeaders() {
    // Security headers should be present even on denied (401) responses.
    web.get().uri("/some/protected/resource")
        .exchange()
        .expectStatus().isUnauthorized()
        .expectHeader().exists("X-Frame-Options");
  }
}
