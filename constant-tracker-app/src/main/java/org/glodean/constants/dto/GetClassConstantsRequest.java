package org.glodean.constants.dto;

import java.io.Serializable;

/**
 * Request DTO used to fetch constants for a specific class within a project and version.
 *
 * <p>This DTO represents a search query for constant usage data. It uniquely identifies
 * a class by the triple (project, className, version), allowing version-aware queries.
 *
 * <p><b>Example usage:</b>
 * <pre>{@code
 * // Query Java 8's String class
 * var request = new GetClassConstantsRequest("java/lang/String", "jdk", 8);
 * String lookupKey = request.key(); // "jdk:java/lang/String:8"
 * }</pre>
 *
 * @param className the class internal or binary name (slash-separated, e.g., "java/lang/String")
 * @param project the project identifier (e.g., "jdk", "spring-boot", "my-app")
 * @param version the project version number (e.g., 8, 11, 17 for JDK versions)
 */
public record GetClassConstantsRequest(String className, String project, int version)
    implements Serializable {
  /**
   * Build the lookup key used by the storage layer.
   *
   * <p>The key format is {@code project:className:version}, which uniquely identifies
   * a class version in the storage system (e.g., Solr document ID).
   *
   * @return composite key string (e.g., "jdk:java/lang/String:8")
   */
  public String key() {
    return project + ":" + className + ":" + version;
  }
}
