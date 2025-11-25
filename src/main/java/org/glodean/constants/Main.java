package org.glodean.constants;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Application entry point for the Constant Tracker Spring Boot application.
 *
 * <p>Starts the Spring context and enables caching. Keep this class minimal; application
 * configuration is provided via Spring beans and configuration files.
 */
@SpringBootApplication
@EnableCaching
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
