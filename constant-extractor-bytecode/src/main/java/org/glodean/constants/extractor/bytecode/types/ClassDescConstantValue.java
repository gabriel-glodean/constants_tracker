package org.glodean.constants.extractor.bytecode.types;

import java.lang.constant.ClassDesc;
import java.util.Map;

/**
 * Structured representation for a JVM class-descriptor constant (LDC of a class literal).
 *
 * <p>Distinguishes plain class/interface descriptors from array descriptors so the persisted
 * {@code constant_value_type} matches the {@code "ClassDescriptor"} / {@code "ArrayDesc"} tokens
 * in the database schema.
 *
 * <p>The {@link #javaName()} field uses Java dot notation (e.g. {@code java.lang.String},
 * {@code int[]}) — consistent with {@code UsageLocation.className}.
 */
public record ClassDescConstantValue(String javaName, boolean isArray) implements StructuredConstantValue {

  public static ClassDescConstantValue from(ClassDesc desc) {
    return new ClassDescConstantValue(ClassDescNames.toJavaName(desc), desc.isArray());
  }

  @Override
  public String constantValueType() {
    return isArray ? "ArrayDesc" : "ClassDescriptor";
  }

  @Override
  public String storageValue() {
    return javaName;
  }

  @Override
  public Map<String, Object> attributes() {
    return Map.of("javaName", javaName, "isArray", isArray);
  }
}
