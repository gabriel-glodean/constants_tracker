package org.glodean.constants.store.solr;

import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SolrConfiguration {
  @Bean
  public HttpSolrClientBase solrClient(@Value("${constants.solr.url}") String url) {
    return new HttpJdkSolrClient.Builder(url).build();
  }
}
