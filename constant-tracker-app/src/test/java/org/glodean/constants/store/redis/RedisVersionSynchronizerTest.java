package org.glodean.constants.store.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import com.redis.testcontainers.RedisContainer;
import java.time.LocalDateTime;
import org.glodean.constants.store.postgres.ProjectVersionEntity;
import org.glodean.constants.store.postgres.ProjectVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.support.atomic.RedisAtomicInteger;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

@Testcontainers
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisVersionSynchronizer")
class RedisVersionSynchronizerTest {

  @Container
  static RedisContainer redis = new RedisContainer("redis:7");

  @Mock
  ProjectVersionRepository versionRepository;

  LettuceConnectionFactory connectionFactory;
  RedisVersionSynchronizer synchronizer;

  @BeforeEach
  void setUp() {
    connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getFirstMappedPort());
    connectionFactory.afterPropertiesSet();
    synchronizer = new RedisVersionSynchronizer(versionRepository, connectionFactory);
  }

  @AfterEach
  void tearDown() {
    // Flush Redis between tests to avoid counter bleed-through
    try (var conn = connectionFactory.getConnection()) {
      conn.serverCommands().flushDb();
    }
    connectionFactory.destroy();
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private static ProjectVersionEntity version(String project, int version) {
    return new ProjectVersionEntity(
        (long) version, project, version, null,
        ProjectVersionEntity.STATUS_OPEN, LocalDateTime.now(), null);
  }

  private int redisCounter(String project) {
    return new RedisAtomicInteger("IdCounter:" + project, connectionFactory).get();
  }

  private void setRedisCounter(String project, int value) {
    new RedisAtomicInteger("IdCounter:" + project, connectionFactory).set(value);
  }

  private void run() {
    synchronizer.run(new DefaultApplicationArguments());
  }

  // ── tests ────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("advances Redis counter when DB version is greater than current Redis value")
  void advancesCounter_whenDbAhead() {
    when(versionRepository.findAll()).thenReturn(
        Flux.just(version("proj", 5)));

    setRedisCounter("proj", 2);
    run();

    assertThat(redisCounter("proj")).isEqualTo(5);
  }

  @Test
  @DisplayName("does NOT change Redis counter when DB version equals current Redis value")
  void noChange_whenDbEqualsRedis() {
    when(versionRepository.findAll()).thenReturn(
        Flux.just(version("proj", 3)));

    setRedisCounter("proj", 3);
    run();

    assertThat(redisCounter("proj")).isEqualTo(3);
  }

  @Test
  @DisplayName("does NOT decrease Redis counter when it is already ahead of the DB")
  void doesNotDecrease_whenRedisAhead() {
    when(versionRepository.findAll()).thenReturn(
        Flux.just(version("proj", 2)));

    setRedisCounter("proj", 10);
    run();

    assertThat(redisCounter("proj")).isEqualTo(10);
  }

  @Test
  @DisplayName("uses the maximum DB version when a project has multiple version rows")
  void usesMaxVersion_forMultipleRows() {
    when(versionRepository.findAll()).thenReturn(
        Flux.just(
            version("proj", 1),
            version("proj", 7),
            version("proj", 4)));

    run();

    assertThat(redisCounter("proj")).isEqualTo(7);
  }

  @Test
  @DisplayName("synchronizes counters independently for multiple projects")
  void syncedIndependently_forMultipleProjects() {
    when(versionRepository.findAll()).thenReturn(
        Flux.just(
            version("alpha", 3),
            version("beta", 9)));

    setRedisCounter("alpha", 1);
    setRedisCounter("beta", 9); // already up-to-date

    run();

    assertThat(redisCounter("alpha")).isEqualTo(3);
    assertThat(redisCounter("beta")).isEqualTo(9);
  }

  @Test
  @DisplayName("leaves Redis untouched when the DB has no version rows")
  void noOp_whenDbEmpty() {
    when(versionRepository.findAll()).thenReturn(Flux.empty());

    run();

    // counter was never written — default RedisAtomicInteger value is 0
    assertThat(redisCounter("proj")).isZero();
  }

  @Test
  @DisplayName("initializes counter from zero when Redis has no entry for the project")
  void initializesFromZero_whenNoRedisKey() {
    when(versionRepository.findAll()).thenReturn(
        Flux.just(version("fresh", 6)));

    run();

    assertThat(redisCounter("fresh")).isEqualTo(6);
  }

  @Test
  @DisplayName("does not throw and swallows repository exceptions to avoid blocking startup")
  void swallowsException_onRepositoryFailure() {
    when(versionRepository.findAll()).thenReturn(
        Flux.error(new RuntimeException("DB unavailable")));

    assertThatCode(this::run).doesNotThrowAnyException();
  }
}

