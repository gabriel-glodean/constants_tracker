package org.glodean.constants.dto;

import java.io.Serializable;

/**
 * Request DTO used to fetch constants for a specific class within a project and version.
 *
 * @param className the class internal or binary name
 * @param project the project identifier
 * @param version the project version number
 */
public record GetClassConstantsRequest(String className, String project, int version)
    implements Serializable {
  /** Build the lookup key used by the storage layer. Format: {@code project:className:version}. */
  public String key() {
    return project + ":" + className + ":" + version;
  }
}
