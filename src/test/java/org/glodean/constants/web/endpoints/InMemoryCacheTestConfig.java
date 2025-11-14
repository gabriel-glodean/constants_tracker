package org.glodean.constants.web.endpoints;

import static org.glodean.constants.store.Constants.DATA_LOCATION;

import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.glodean.constants.store.solr.SolrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class InMemoryCacheTestConfig {

  @Bean
  public CacheManager cacheManager() {
    // Provide cache names if you use @Cacheable("..."):
    return new ConcurrentMapCacheManager(DATA_LOCATION);
  }

  @Bean
  SolrService solrService(@Autowired HttpSolrClientBase solrClient) {
    return new SolrService(solrClient, (_, _) -> 1);
  }
}
