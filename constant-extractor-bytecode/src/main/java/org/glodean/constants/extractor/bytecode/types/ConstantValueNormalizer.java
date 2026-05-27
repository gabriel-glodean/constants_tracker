package org.glodean.constants.extractor.bytecode.types;

import java.lang.constant.ClassDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;

/**
 * Normalizes JDK-specific constant descriptor implementations into stable domain values.
 *
 * <p>After normalization every non-primitive, non-string constant is a {@link StructuredConstantValue},
 * so callers never need to inspect raw JDK implementation types.
 */
public final class ConstantValueNormalizer {

  private ConstantValueNormalizer() {}

  public static Object normalize(Object value) {
    if (value instanceof DynamicConstantDesc<?> dynamicConstantDesc) {
      return DynamicConstantValue.from(dynamicConstantDesc);
    }
    if (value instanceof MethodHandleDesc methodHandleDesc) {
      return MethodHandleConstantValue.from(methodHandleDesc);
    }
    if (value instanceof ClassDesc classDesc) {
      return ClassDescConstantValue.from(classDesc);
    }
    return value;
  }
}
