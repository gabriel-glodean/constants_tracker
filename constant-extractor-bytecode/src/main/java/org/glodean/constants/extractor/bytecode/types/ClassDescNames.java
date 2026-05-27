package org.glodean.constants.extractor.bytecode.types;

import java.lang.constant.ClassDesc;

/**
 * Converts JVM {@link ClassDesc} values to Java dot-notation names.
 *
 * <p>Mirrors the logic in {@code Utils.toJavaName()} (package-private in the parent package)
 * so that structured constant-value types can produce names consistent with
 * {@code UsageLocation.className} without depending on a package-private helper.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code Ljava/lang/String;} → {@code java.lang.String}</li>
 *   <li>{@code I}                  → {@code int}</li>
 *   <li>{@code [Ljava/lang/String;} → {@code java.lang.String[]}</li>
 * </ul>
 */
final class ClassDescNames {

  private ClassDescNames() {}

  /**
   * Returns the fully-qualified Java name for the given class descriptor.
   */
  static String toJavaName(ClassDesc desc) {
    if (desc.isPrimitive()) return desc.displayName();
    if (desc.isArray()) return toJavaName(desc.componentType()) + "[]";
    String d = desc.descriptorString(); // Lcom/example/Foo;
    return d.substring(1, d.length() - 1).replace('/', '.');
  }
}
