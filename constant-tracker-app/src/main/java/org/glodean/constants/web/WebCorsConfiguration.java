package org.glodean.constants.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Global CORS configuration for the WebFlux application.
 *
 * <p>Allows the React search-ui dev server (default {@code http://localhost:5173}) to call the API
 * without running into browser same-origin restrictions. The allowed origin is configurable via
 * {@code constants.cors.allowed-origin} in {@code application.yaml}.
 */
@Configuration
public class WebCorsConfiguration {

  @Value("${constants.cors.allowed-origin:http://localhost:5173}")
  private String allowedOrigin;

  @Bean
  CorsWebFilter corsWebFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.addAllowedOrigin(allowedOrigin);
    config.addAllowedMethod("GET");
    config.addAllowedMethod("POST");
    config.addAllowedMethod("PUT");
    config.addAllowedMethod("OPTIONS");
    config.addAllowedHeader("*");
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsWebFilter(source);
  }
}
