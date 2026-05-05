package org.glodean.constants.web.endpoints;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.glodean.constants.auth.JwtService;
import org.glodean.constants.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import java.util.List;
/**
 * Slice tests for {@link LoginController}.
 *
 * <p>Uses a permissive security chain so that no Bearer token is required to reach the
 * controller methods — business logic is verified entirely through the mocked
 * {@link JwtService}.
 */
@WebFluxTest(controllers = LoginController.class)
@Import(LoginControllerTest.OpenSecurity.class)
class LoginControllerTest {
  /** Minimal security config: permit all requests and inject a stub authenticated principal. */
  @TestConfiguration
  static class OpenSecurity {
    @Bean
    SecurityWebFilterChain chain(ServerHttpSecurity http) {
      http.csrf(ServerHttpSecurity.CsrfSpec::disable);
      http.authorizeExchange(ex -> ex.anyExchange().permitAll());
      // Inject stub principal so Authentication parameter in controller methods is non-null.
      http.addFilterAt(
          (exchange, chain2) -> chain2.filter(exchange).contextWrite(
              ReactiveSecurityContextHolder.withAuthentication(
                  new UsernamePasswordAuthenticationToken(
                      "test-user", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))),
          SecurityWebFiltersOrder.AUTHENTICATION);
      return http.build();
    }
  }
  @Autowired WebTestClient web;
  @MockitoBean JwtService jwtService;
  // -------------------------------------------------------------------------
  // POST /auth/login
  // -------------------------------------------------------------------------
  @Test
  void login_validCredentials_returns200WithToken() {
    when(jwtService.authenticate("alice", "secret"))
        .thenReturn(Mono.just(new TokenResponse("eyJ.test.token", "refresh-xyz", 3_600_000L)));
    web.post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"alice\",\"password\":\"secret\"}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.accessToken").isEqualTo("eyJ.test.token")
        .jsonPath("$.refreshToken").isEqualTo("refresh-xyz")
        .jsonPath("$.expiresInMs").isEqualTo(3_600_000);
  }
  @Test
  void login_badCredentials_returns401() {
    when(jwtService.authenticate(anyString(), anyString()))
        .thenReturn(Mono.error(new BadCredentialsException("Invalid credentials")));
    web.post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"alice\",\"password\":\"wrong\"}")
        .exchange()
        .expectStatus().isUnauthorized();
  }
  @Test
  void login_blankUsername_returns400() {
    web.post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"\",\"password\":\"secret\"}")
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.status").isEqualTo(400)
        .jsonPath("$.message").value(msg -> org.assertj.core.api.Assertions
            .assertThat((String) msg).contains("username"));
  }
  @Test
  void login_blankPassword_returns400() {
    web.post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"alice\",\"password\":\"\"}")
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.status").isEqualTo(400)
        .jsonPath("$.message").value(msg -> org.assertj.core.api.Assertions
            .assertThat((String) msg).contains("password"));
  }
  @Test
  void login_nullUsername_returns400() {
    web.post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":null,\"password\":\"secret\"}")
        .exchange()
        .expectStatus().isBadRequest();
  }
  // -------------------------------------------------------------------------
  // POST /auth/token/renew
  // -------------------------------------------------------------------------
  @Test
  void renewToken_validHeader_returns200WithNewTokenPair() {
    when(jwtService.invalidate(anyString())).thenReturn(Mono.empty());
    when(jwtService.renewToken(anyString()))
        .thenReturn(Mono.just(new TokenResponse("eyJ.new.token", "new-refresh-rt", 3_600_000L)));
    web.post()
        .uri("/auth/token/renew")
        .header("Authorization", "Bearer eyJ.old.token")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.accessToken").isEqualTo("eyJ.new.token")
        .jsonPath("$.refreshToken").isEqualTo("new-refresh-rt");
  }
  // -------------------------------------------------------------------------
  // POST /auth/refresh
  // -------------------------------------------------------------------------
  @Test
  void refresh_validRefreshToken_returns200WithNewTokenPair() {
    when(jwtService.refresh("raw-rt"))
        .thenReturn(Mono.just(new TokenResponse("eyJ.new.access", "new-refresh-rt", 3_600_000L)));
    web.post()
        .uri("/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"refreshToken\":\"raw-rt\"}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.accessToken").isEqualTo("eyJ.new.access")
        .jsonPath("$.refreshToken").isEqualTo("new-refresh-rt");
  }
  @Test
  void refresh_invalidRefreshToken_returns401() {
    when(jwtService.refresh(anyString()))
        .thenReturn(Mono.error(new org.springframework.security.authentication.BadCredentialsException("Invalid")));
    web.post()
        .uri("/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"refreshToken\":\"bad-token\"}")
        .exchange()
        .expectStatus().isUnauthorized();
  }
  // -------------------------------------------------------------------------
  // POST /auth/logout
  // -------------------------------------------------------------------------
  @Test
  void logout_validHeader_returns204() {
    when(jwtService.logout(anyString(), isNull())).thenReturn(Mono.empty());
    web.post()
        .uri("/auth/logout")
        .header("Authorization", "Bearer eyJ.some.token")
        .exchange()
        .expectStatus().isNoContent();
    verify(jwtService).logout("eyJ.some.token", null);
  }
  @Test
  void logout_withRefreshToken_revokesIt() {
    when(jwtService.logout(anyString(), eq("my-refresh-token"))).thenReturn(Mono.empty());
    web.post()
        .uri("/auth/logout")
        .header("Authorization", "Bearer eyJ.some.token")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"refreshToken\":\"my-refresh-token\"}")
        .exchange()
        .expectStatus().isNoContent();
    verify(jwtService).logout("eyJ.some.token", "my-refresh-token");
  }
  @Test
  void logout_headerWithoutBearer_passesTokenAsIs() {
    when(jwtService.logout(anyString(), isNull())).thenReturn(Mono.empty());
    web.post()
        .uri("/auth/logout")
        .header("Authorization", "raw-token-without-bearer")
        .exchange()
        .expectStatus().isNoContent();
    verify(jwtService).logout("raw-token-without-bearer", null);
  }
}
