package org.glodean.constants.store;

import java.util.Collection;
import java.util.Map;
import org.glodean.constants.dto.FuzzySearchResponse;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstants;
import reactor.core.publisher.Mono;

/**
 * Store interface for managing {@link ClassConstants} in a project context.
 *
 * <p>This interface abstracts the persistence layer for constant usage analysis results.
 * Implementations (e.g., Solr-based, SQL-based) can store and query constant data with
 * support for:
 * <ul>
 *   <li><b>Project isolation:</b> Constants are scoped to project identifiers</li>
 *   <li><b>Version tracking:</b> Multiple versions of the same class can coexist</li>
 *   <li><b>Reactive API:</b> Returns {@link Mono} for non-blocking I/O integration</li>
 *   <li><b>Search capabilities:</b> Find constants by various criteria (class name, value, usage type)</li>
 * </ul>
 *
 * <p><b>Typical workflow:</b>
 * <pre>{@code
 * // Store analysis results
 * ClassConstants results = extractor.extract();
 * store.store(results, "my-project", 1).subscribe();
 *
 * // Search for constant usages
 * store.find("com.example.MyClass").subscribe(usages -> {
 *     // Process found constants
 * });
 * }</pre>
 */
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

  /**
   * Full-text / fuzzy search for class snapshots whose constant values match {@code term}.
   *
   * <p>The caller supplies a plain-text search term; no Lucene syntax knowledge is required.
   * Search-engine specific query construction (escaping, fuzzy suffixes, field boosting) is
   * handled internally by the backing implementation.
   *
   * <p>The default implementation delegates the operation as unsupported. Implementations backed
   * by a search engine (e.g. Solr) should override this method.
   *
   * @param project      project to restrict results to; use {@code "*"} for cross-project search
   * @param term         plain-text search term (special characters are escaped automatically)
   * @param editDistance fuzzy tolerance per token: {@code 0} = exact, {@code 1} = one typo,
   *                     {@code 2} = two typos
   * @param maxRows      maximum number of hits to return
   * @return {@link Mono} emitting a {@link FuzzySearchResponse}
   */
  default Mono<FuzzySearchResponse> fuzzySearch(
      String project, String term, int editDistance, int maxRows) {
    return Mono.error(new UnsupportedOperationException("Fuzzy search not supported by this store"));
  }
}
