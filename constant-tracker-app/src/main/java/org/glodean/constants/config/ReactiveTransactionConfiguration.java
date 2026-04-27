package org.glodean.constants.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Exposes a {@link TransactionalOperator} bean for use in reactive components that cannot
 * rely on Spring's AOP {@code @Transactional} proxy (e.g. {@code @Scheduled} methods that
 * subscribe internally, such as {@link org.glodean.constants.store.solr.SolrOutboxProcessor}).
 */
@Configuration
public class ReactiveTransactionConfiguration {

  /**
   * Creates a {@link TransactionalOperator} backed by the auto-configured
   * {@link ReactiveTransactionManager} (provided by {@code spring-boot-starter-data-r2dbc}).
   *
   * @param transactionManager the R2DBC reactive transaction manager
   * @return a ready-to-use transactional operator
   */
  @Bean
  public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
    return TransactionalOperator.create(transactionManager);
  }
}
