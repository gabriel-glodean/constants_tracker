package org.glodean.constants.dto;

import java.io.Serializable;

public record GetClassConstantsRequest(String className, String project, int version)
    implements Serializable {
  public String key() {
    return project + ":" + className + ":" + version;
  }
}
