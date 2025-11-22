package org.glodean.constants.store;

import java.util.Collection;
import java.util.Map;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstants;
import reactor.core.publisher.Mono;

/** Store interface for managing {@link ClassConstants} in a project context. */
public interface ClassConstantsStore {

  /**
   * Stores the given {@link ClassConstants} for a specific project and version.
   *
   * @param constants the class constants to store
   * @param project the project identifier
   * @param version the version number
   * @return a {@link Mono} emitting the stored {@link ClassConstants}
   */
  Mono<ClassConstants> store(ClassConstants constants, String project, int version);

  /**
   * Stores the given {@link ClassConstants} for a specific project.
   *
   * @param constants the class constants to store
   * @param project the project identifier
   * @return a {@link Mono} emitting the stored {@link ClassConstants}
   */
  Mono<ClassConstants> store(ClassConstants constants, String project);

  /**
   * Finds usages of class constants by key.
   *
   * @param key the key to search for
   * @return a {@link Mono} emitting a map of usages grouped by type
   */
  Mono<Map<Object, Collection<ClassConstant.UsageType>>> find(String key);
}
