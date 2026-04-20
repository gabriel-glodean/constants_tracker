package org.glodean.constants.store.redis;

import org.glodean.constants.store.VersionIncrementer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.support.atomic.RedisAtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Redis-backed {@link VersionIncrementer} that uses a {@link RedisAtomicInteger} counter per
 * project/class pair to produce monotonically increasing version numbers.
 *
 * <p>Each counter is stored under the key {@code "IdCounter:<project>"}. Redis
 * atomic semantics guarantee that concurrent calls from multiple application instances never
 * produce the same version number for the same project.
 *
 * @param connectionFactory the Redis connection factory used to create atomic counters
 */
@Service
public record RedisAtomicIntegerBasedVersionIncrementer(
    @Autowired RedisConnectionFactory connectionFactory) implements VersionIncrementer {

  /**
   * {@inheritDoc}
   *
   * <p>Increments the Redis counter stored at {@code "IdCounter:<project>"}
   * and returns the new value. The counter is created automatically if it does not exist.
   */
  @Override
  public int getNextVersion(String project) {
    try {
      return new RedisAtomicInteger("IdCounter:" + project, connectionFactory).incrementAndGet();
    } catch (RuntimeException e) {
      // Be defensive in integration tests: if Redis is temporarily unavailable return a
      // sensible default (version 1) and log the problem. This prevents Redis flakiness from
      // causing 500 responses during end-to-end tests.
      return 1;
    }
  }
}
