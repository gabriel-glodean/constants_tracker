package org.glodean.constants.auth;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.glodean.constants.store.postgres.entity.AuthTokenBlacklistEntity;
import org.glodean.constants.store.postgres.repository.AuthTokenBlacklistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
@ExtendWith(MockitoExtension.class)
class AuthTokenBlacklistServiceTest {
  @Mock AuthTokenBlacklistRepository blacklistRepository;
  @Mock ReactiveStringRedisTemplate redis;
  @Mock ReactiveValueOperations<String, String> redisOps;
  private AuthTokenBlacklistService service;
  @BeforeEach
  void setUp() {
    lenient().when(redis.opsForValue()).thenReturn(redisOps);
    service = new AuthTokenBlacklistService(blacklistRepository, redis);
  }
  @Test
  void blacklist_positiveTtl_writesRedisWithTtlAndDb() {
    String jti = "jti-1";
    OffsetDateTime expiresAt = OffsetDateTime.now().plus(Duration.ofHours(1));
    var entity = new AuthTokenBlacklistEntity(jti, 1L, expiresAt, OffsetDateTime.now());
    when(redisOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
    when(blacklistRepository.save(any())).thenReturn(Mono.just(entity));
    StepVerifier.create(service.blacklist(jti, 1L, expiresAt)).verifyComplete();
    verify(redisOps).set(eq("blacklist:jti:" + jti), eq("1"), any(Duration.class));
    verify(blacklistRepository).save(any());
  }
  @Test
  void blacklist_negativeTtl_writesRedisWithoutTtlAndDb() {
    String jti = "jti-2";
    OffsetDateTime expiresAt = OffsetDateTime.now().minus(Duration.ofHours(1));
    var entity = new AuthTokenBlacklistEntity(jti, 2L, expiresAt, OffsetDateTime.now());
    when(redisOps.set(anyString(), anyString())).thenReturn(Mono.just(true));
    when(blacklistRepository.save(any())).thenReturn(Mono.just(entity));
    StepVerifier.create(service.blacklist(jti, 2L, expiresAt)).verifyComplete();
    verify(redisOps).set(eq("blacklist:jti:" + jti), eq("1"));
    verify(blacklistRepository).save(any());
  }
  @Test
  void blacklist_redisWriteFails_stillPersistsToDb() {
    String jti = "jti-3";
    OffsetDateTime expiresAt = OffsetDateTime.now().plus(Duration.ofHours(1));
    var entity = new AuthTokenBlacklistEntity(jti, 3L, expiresAt, OffsetDateTime.now());
    when(redisOps.set(anyString(), anyString(), any(Duration.class)))
        .thenReturn(Mono.error(new RuntimeException("Redis down")));
    when(blacklistRepository.save(any())).thenReturn(Mono.just(entity));
    StepVerifier.create(service.blacklist(jti, 3L, expiresAt)).verifyComplete();
    verify(blacklistRepository).save(any());
  }
  @Test
  void isBlacklisted_redisHit_returnsTrue() {
    when(redis.hasKey("blacklist:jti:jti-4")).thenReturn(Mono.just(true));
    StepVerifier.create(service.isBlacklisted("jti-4")).expectNext(true).verifyComplete();
    verifyNoInteractions(blacklistRepository);
  }
  @Test
  void isBlacklisted_redisMissDbHasActiveEntry_returnsTrue() {
    String jti = "jti-5";
    OffsetDateTime futureExpiry = OffsetDateTime.now().plus(Duration.ofHours(1));
    var entity = new AuthTokenBlacklistEntity(jti, 5L, futureExpiry, OffsetDateTime.now());
    when(redis.hasKey("blacklist:jti:" + jti)).thenReturn(Mono.just(false));
    when(blacklistRepository.findById(jti)).thenReturn(Mono.just(entity));
    when(redisOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
    StepVerifier.create(service.isBlacklisted(jti)).expectNext(true).verifyComplete();
  }
  @Test
  void isBlacklisted_redisMissDbHasExpiredEntry_returnsFalse() {
    String jti = "jti-6";
    OffsetDateTime pastExpiry = OffsetDateTime.now().minus(Duration.ofHours(1));
    var entity = new AuthTokenBlacklistEntity(jti, 6L, pastExpiry, OffsetDateTime.now());
    when(redis.hasKey("blacklist:jti:" + jti)).thenReturn(Mono.just(false));
    when(blacklistRepository.findById(jti)).thenReturn(Mono.just(entity));
    StepVerifier.create(service.isBlacklisted(jti)).expectNext(false).verifyComplete();
  }
  @Test
  void isBlacklisted_redisMissDbEmpty_returnsFalse() {
    String jti = "jti-7";
    when(redis.hasKey("blacklist:jti:" + jti)).thenReturn(Mono.just(false));
    when(blacklistRepository.findById(jti)).thenReturn(Mono.empty());
    StepVerifier.create(service.isBlacklisted(jti)).expectNext(false).verifyComplete();
  }
  @Test
  void isBlacklisted_redisError_fallsBackToDb() {
    String jti = "jti-8";
    OffsetDateTime futureExpiry = OffsetDateTime.now().plus(Duration.ofHours(1));
    var entity = new AuthTokenBlacklistEntity(jti, 8L, futureExpiry, OffsetDateTime.now());
    when(redis.hasKey("blacklist:jti:" + jti))
        .thenReturn(Mono.error(new RuntimeException("Redis unavailable")));
    when(blacklistRepository.findById(jti)).thenReturn(Mono.just(entity));
    StepVerifier.create(service.isBlacklisted(jti)).expectNext(true).verifyComplete();
  }
  @Test
  void isBlacklisted_redisErrorAndDbEmpty_returnsFalse() {
    String jti = "jti-9";
    when(redis.hasKey("blacklist:jti:" + jti))
        .thenReturn(Mono.error(new RuntimeException("Redis unavailable")));
    when(blacklistRepository.findById(jti)).thenReturn(Mono.empty());
    StepVerifier.create(service.isBlacklisted(jti)).expectNext(false).verifyComplete();
  }
  @Test
  void purgeExpired_delegatesToRepository() {
    when(blacklistRepository.deleteByExpiresAtBefore(any())).thenReturn(Mono.empty());
    StepVerifier.create(service.purgeExpired()).verifyComplete();
    verify(blacklistRepository).deleteByExpiresAtBefore(any());
  }
}
