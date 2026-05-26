package org.glodean.constants.util;

import java.util.Map;

/** Maps raw JVM/runtime constant value types to stable API-facing type names. */
public final class ConstantValueTypeMapper {

  private static final String NULL_TYPE = "Null";

  private static final Map<String, String> TYPE_MAPPINGS = Map.of(
      "AnonymousDynamicConstantDesc", "DynamicConstant",
      "ClassOrInterfaceDescImpl", "ClassDescriptor",
      "ArrayClassDescImpl", "ArrayDesc",
      "null", NULL_TYPE);

  private ConstantValueTypeMapper() {}

  public static String map(Object value) {
    if (value == null) {
      return NULL_TYPE;
    }
    return mapRawTypeName(value.getClass().getSimpleName());
  }

  public static String mapRawTypeName(String rawTypeName) {
    if (rawTypeName == null) {
      return NULL_TYPE;
    }
    return TYPE_MAPPINGS.getOrDefault(rawTypeName, rawTypeName);
  }
}
