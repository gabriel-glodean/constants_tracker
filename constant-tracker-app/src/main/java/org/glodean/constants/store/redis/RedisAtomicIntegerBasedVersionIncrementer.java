package org.glodean.constants.store.redis;

import org.glodean.constants.store.VersionIncrementer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.support.atomic.RedisAtomicInteger;
import org.springframework.stereotype.Service;

@Service
public record RedisAtomicIntegerBasedVersionIncrementer(
    @Autowired RedisConnectionFactory connectionFactory) implements VersionIncrementer {
  @Override
  public int getNextVersion(String project, String className) {
    return new RedisAtomicInteger("IdCounter:" + project + ":" + className, connectionFactory)
        .incrementAndGet();
  }
}
