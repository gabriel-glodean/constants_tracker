package org.glodean.constants.store;

/**
 * Strategy for producing monotonically increasing version numbers for a project/class pair.
 *
 * <p>Implementations may use persistent counters, atomic integers stored in a backing store, or
 * derive versions from timestamps. The contract is simple: given a project and class name return
 * the next integer version to assign when persisting new analysis results.
 */
public interface VersionIncrementer {
  int getNextVersion(String project, String className);
}
