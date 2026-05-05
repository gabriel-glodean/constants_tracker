package org.glodean.constants;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point for the Constant Tracker Spring Boot application.
 *
 * <p>Starts the Spring context, enables caching, and enables scheduled task execution
 * (required by {@link org.glodean.constants.store.solr.SolrOutboxProcessor}).
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@ConfigurationPropertiesScan
public class Main {
  /**
   * Main method that launches the Spring Boot application.
   *
   * @param args application arguments passed to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }
}
