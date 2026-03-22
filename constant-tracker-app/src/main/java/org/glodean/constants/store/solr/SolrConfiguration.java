package org.glodean.constants.store.solr;

import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring configuration that builds a Solr client used by the Solr-backed storage service. */
@Configuration
public class SolrConfiguration {
  /**
   * Creates the Solr HTTP client pointed at the configured collection URL.
   *
   * @param url the base Solr URL, e.g. {@code http://localhost:8983/solr/Constants},
   *            injected from the {@code constants.solr.url} property
   * @return a ready-to-use {@link HttpSolrClientBase}
   */
  @Bean
  public HttpSolrClientBase solrClient(@Value("${constants.solr.url}") String url) {
    return new HttpJdkSolrClient.Builder(url).build();
  }
}
