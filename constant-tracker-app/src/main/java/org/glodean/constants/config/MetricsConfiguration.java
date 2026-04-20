package org.glodean.constants.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link TimedAspect} bean so that {@code @Timed} annotations on service methods
 * are picked up and recorded as Micrometer timer metrics.
 */
@Configuration
public class MetricsConfiguration {

  @Bean
  public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }
}

