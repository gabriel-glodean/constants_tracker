package org.glodean.constants.samples;

public class TypeCheckingFunctionality {
  public boolean isString(Object value) {
    return value instanceof String;
  }

  public int stringLength(Object value) {
    return ((String) value).length();
  }
}
