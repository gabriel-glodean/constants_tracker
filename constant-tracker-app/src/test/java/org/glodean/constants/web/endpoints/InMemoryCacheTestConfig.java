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
}
