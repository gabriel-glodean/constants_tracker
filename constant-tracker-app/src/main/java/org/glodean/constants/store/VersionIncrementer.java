package org.glodean.constants.store;

/**
 * Strategy for producing monotonically increasing version numbers for a project.
 *
 * <p>Implementations may use persistent counters, atomic integers stored in a backing store, or
 * derive versions from timestamps. The contract is simple: given a project return
 * the next integer version to assign when persisting new analysis results.
 *
 * <p><b>Thread-safety:</b> Implementations must be thread-safe, as multiple concurrent requests
 * may attempt to store different versions of the same project. Use atomic operations or locking
 * to prevent version collisions.
 *
 * <p><b>Example use case:</b>
 * <pre>{@code
 * int v1 = incrementer.getNextVersion("jdk");   // Returns 1
 * int v2 = incrementer.getNextVersion("jdk");   // Returns 2
 * int v3 = incrementer.getNextVersion("jdk");   // Returns 3
 * }</pre>
 *
 * @see org.glodean.constants.store.redis.RedisAtomicIntegerBasedVersionIncrementer
 */
public interface VersionIncrementer {
  /**
   * Returns the next version number for the given project.
   *
   * @param project the project identifier (e.g., "jdk", "spring-boot", "my-app")
   * @return a monotonically increasing version number (starting from 1)
   */
  int getNextVersion(String project);
}
