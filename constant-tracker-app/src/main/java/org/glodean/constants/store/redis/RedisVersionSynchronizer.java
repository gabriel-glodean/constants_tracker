package org.glodean.constants.store.redis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.store.postgres.entity.ProjectVersionEntity;
import org.glodean.constants.store.postgres.repository.ProjectVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.support.atomic.RedisAtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Synchronizes Redis version counters with the authoritative state stored in PostgreSQL.
 *
 * <p>The Redis counter {@code IdCounter:<project>} drives {@link RedisAtomicIntegerBasedVersionIncrementer}.
 * If the application restarts after data is written — or if Redis is flushed — the counter may
 * be behind the highest version already persisted in the database. Without correction the
 * incrementer would reissue version numbers that already exist, causing silent overwrites.
 *
 * <p>This component runs once at startup (before the server accepts traffic, via
 * {@link ApplicationRunner}) and for every project found in {@code project_versions} it
 * sets the Redis counter to {@code max(redisValue, maxDbVersion)} so the next call to
 * {@link RedisAtomicIntegerBasedVersionIncrementer#getNextVersion} will always return a
 * value strictly greater than the highest persisted version.
 */
@Component
public class RedisVersionSynchronizer implements ApplicationRunner {

  private static final Logger logger = LogManager.getLogger(RedisVersionSynchronizer.class);

  private final ProjectVersionRepository versionRepository;
  private final RedisConnectionFactory connectionFactory;

  public RedisVersionSynchronizer(
      @Autowired ProjectVersionRepository versionRepository,
      @Autowired RedisConnectionFactory connectionFactory) {
    this.versionRepository = versionRepository;
    this.connectionFactory = connectionFactory;
  }

  /**
   * Reads every {@link ProjectVersionEntity} row, determines the maximum version number per
   * project, and advances the corresponding Redis counter when it lags behind.
   *
   * <p>Uses {@code .block()} because this is initialization work that must complete before the
   * application starts serving requests.
   */
  @Override
  public void run(ApplicationArguments args) {
    logger.atInfo().log("Starting Redis ↔ DB version counter synchronization…");

    try {
      versionRepository
          .findAll()
          // group into Map<project, maxVersion>
          .collectMultimap(ProjectVersionEntity::project, ProjectVersionEntity::version)
          .doOnNext(projectVersionMap -> {
            for (var entry : projectVersionMap.entrySet()) {
              String project = entry.getKey();
              int maxDbVersion = entry.getValue().stream()
                  .mapToInt(Integer::intValue)
                  .max()
                  .orElse(0);
              syncCounter(project, maxDbVersion);
            }
          })
          .block();

      logger.atInfo().log("Redis ↔ DB version counter synchronization complete.");
    } catch (Exception e) {
      // Do not abort startup — the app can still serve reads; only new uploads may collide.
      logger.atWarn().withThrowable(e).log(
          "Redis ↔ DB version synchronization failed; counter may be stale. "
              + "New uploads for projects with existing data could receive duplicate version numbers.");
    }
  }

  /**
   * Atomically advances the Redis counter for {@code project} to {@code targetMinimum} if it
   * currently holds a smaller value.
   *
   * <p>Uses a compare-and-set loop so that concurrent incrementers (on other nodes) are not
   * clobbered; we only ever move the counter forward.
   */
  private void syncCounter(String project, int targetMinimum) {
    var counter = new RedisAtomicInteger("IdCounter:" + project, connectionFactory);
    int current = counter.get();
    if (current >= targetMinimum) {
      logger.atDebug().log(
          "Redis counter for '{}' is already up-to-date (redis={}, db-max={})",
          project, current, targetMinimum);
      return;
    }

    // Advance with compareAndSet to avoid racing with live incrementers
    boolean updated = false;
    while (current < targetMinimum) {
      if (counter.compareAndSet(current, targetMinimum)) {
        updated = true;
        break;
      }
      current = counter.get(); // another thread advanced the counter; re-check
    }

    if (updated) {
      logger.atInfo().log(
          "Advanced Redis counter for '{}': {} → {} (db-max={})",
          project, current, targetMinimum, targetMinimum);
    } else {
      logger.atDebug().log(
          "Redis counter for '{}' was concurrently advanced past target; no action taken "
              + "(redis={}, target={})", project, counter.get(), targetMinimum);
    }
  }
}
