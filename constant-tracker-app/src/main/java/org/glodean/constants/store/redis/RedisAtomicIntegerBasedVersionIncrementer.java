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
 * <p>Each counter is stored under the key {@code "IdCounter:<project>:<className>"}. Redis
 * atomic semantics guarantee that concurrent calls from multiple application instances never
 * produce the same version number for the same class.
 *
 * @param connectionFactory the Redis connection factory used to create atomic counters
 */
@Service
public record RedisAtomicIntegerBasedVersionIncrementer(
    @Autowired RedisConnectionFactory connectionFactory) implements VersionIncrementer {

  /**
   * {@inheritDoc}
   *
   * <p>Increments the Redis counter stored at {@code "IdCounter:<project>:<className>"}
   * and returns the new value. The counter is created automatically if it does not exist.
   */
  @Override
  public int getNextVersion(String project, String className) {
    return new RedisAtomicInteger("IdCounter:" + project + ":" + className, connectionFactory)
        .incrementAndGet();
  }
}
