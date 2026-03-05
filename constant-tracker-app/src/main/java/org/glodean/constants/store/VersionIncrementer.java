package org.glodean.constants.store;

/**
 * Strategy for producing monotonically increasing version numbers for a project/class pair.
 *
 * <p>Implementations may use persistent counters, atomic integers stored in a backing store, or
 * derive versions from timestamps. The contract is simple: given a project and class name return
 * the next integer version to assign when persisting new analysis results.
 *
 * <p><b>Thread-safety:</b> Implementations must be thread-safe, as multiple concurrent requests
 * may attempt to store different versions of the same class. Use atomic operations or locking
 * to prevent version collisions.
 *
 * <p><b>Example use case:</b>
 * <pre>{@code
 * // Analyzing JDK evolution
 * int jdk8Version = incrementer.getNextVersion("jdk", "java/lang/String");   // Returns 1
 * int jdk11Version = incrementer.getNextVersion("jdk", "java/lang/String");  // Returns 2
 * int jdk17Version = incrementer.getNextVersion("jdk", "java/lang/String");  // Returns 3
 * }</pre>
 *
 * @see org.glodean.constants.store.redis.RedisAtomicIntegerBasedVersionIncrementer
 */
public interface VersionIncrementer {
  /**
   * Returns the next version number for the given project/class combination.
   *
   * @param project the project identifier (e.g., "jdk", "spring-boot", "my-app")
   * @param className the fully qualified class name (slash-separated, e.g., "java/lang/String")
   * @return a monotonically increasing version number (starting from 1)
   */
  int getNextVersion(String project, String className);
}
