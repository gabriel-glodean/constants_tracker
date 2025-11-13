package org.glodean.constants.web.endpoints;

import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.glodean.constants.store.SolrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.glodean.constants.store.SolrService.DATA_LOCATION;

@TestConfiguration
public class InMemoryCacheTestConfig {

    @Bean
    public CacheManager cacheManager() {
        // Provide cache names if you use @Cacheable("..."):
        return new ConcurrentMapCacheManager(DATA_LOCATION);
    }

    @Bean
    SolrService solrService(@Autowired HttpSolrClientBase solrClient,
                            @Autowired RedisConnectionFactory connectionFactory) {
        return new SolrService(solrClient, connectionFactory);
    }
}