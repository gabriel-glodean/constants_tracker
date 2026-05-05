package org.glodean.constants.web.endpoints;

import static org.glodean.constants.store.Constants.DATA_LOCATION;

import org.glodean.constants.extractor.bytecode.AnalysisMerger;
import org.glodean.constants.extractor.bytecode.InternalStringConcatPatternSplitter;
import org.glodean.constants.services.ConcreteExtractionService;
import org.glodean.constants.services.ExtractionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@TestConfiguration
public class InMemoryCacheTestConfig {

  @Bean
  public CacheManager cacheManager() {
    return new ConcurrentMapCacheManager(DATA_LOCATION);
  }

  @Bean
  ExtractionService extractionService() {
    return new ConcreteExtractionService(
        new AnalysisMerger(new InternalStringConcatPatternSplitter()));
  }

  /** Disable security for controller slice tests — no auth headers required. */
  @Bean
  public SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
    http.csrf(ServerHttpSecurity.CsrfSpec::disable);
    http.authorizeExchange(ex -> ex.anyExchange().permitAll());
    return http.build();
  }
}
