/**
 * Redis store implementation — atomic version counters and Spring Cache integration.
 *
 * <ul>
 *   <li>{@link org.glodean.constants.store.redis.RedisAtomicIntegerBasedVersionIncrementer} —
 *       implements {@link org.glodean.constants.store.VersionIncrementer} using a Redis
 *       {@code INCR} command to produce monotonically increasing, collision-free version
 *       numbers per project key.</li>
 *   <li>{@link org.glodean.constants.store.redis.RedisVersionSynchronizer} — synchronizes
 *       the Redis counter with the latest version already persisted in PostgreSQL on
 *       startup, preventing counter resets after a Redis flush.</li>
 * </ul>
 *
 * <p>Redis connection details are configured via {@code spring.data.redis.*} in
 * {@code application.yaml}. Spring Cache ({@code spring.cache.type=redis}) uses the
 * same connection for method-level result caching.
 */
package org.glodean.constants.store.redis;
