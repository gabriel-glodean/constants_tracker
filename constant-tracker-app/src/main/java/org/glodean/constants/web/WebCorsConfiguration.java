package org.glodean.constants.web;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Global CORS configuration for the WebFlux application.
 *
 * <p>Allows the React search-ui (default {@code http://localhost:5173}) to call the API without
 * running into browser same-origin restrictions. The allowed origins are configurable via {@code
 * constants.cors.allowed-origins} (comma-separated) in {@code application.yaml}.
 */
@Configuration
public class WebCorsConfiguration {

  @Value("${constants.cors.allowed-origins:http://localhost:5173}")
  private String allowedOrigins;

  @Bean
  CorsWebFilter corsWebFilter() {
    List<String> origins =
        Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList();
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(origins);
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
