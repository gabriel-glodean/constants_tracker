package org.glodean.constants.extractor.bytecode.types;

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured representation for a JVM method-handle constant.
 *
 * <p>Class-name fields ({@link #ownerClass()}) use Java dot notation (e.g.
 * {@code java.lang.Integer}) — consistent with {@code UsageLocation.className}.
 * Method-type-descriptor fields ({@link #lookupDescriptor()},
 * {@link #invocationTypeDescriptor()}) retain JVM descriptor format since they
 * encode full parameter/return-type signatures, not bare class names.
 */
public record MethodHandleConstantValue(
    String kind,
    String ownerClass,
    String methodName,
    String lookupDescriptor,
    String invocationTypeDescriptor
) implements StructuredConstantValue {

  /**
   * Builds a {@code MethodHandleConstantValue} from a JDK descriptor.
   *
   * <p>{@link MethodHandleDesc} is a sealed interface whose only permitted subtype is
   * {@link DirectMethodHandleDesc}, so the cast below is always safe for any valid constant pool
   * entry. A {@link ClassCastException} here would indicate a JDK API contract violation.
   */
  public static MethodHandleConstantValue from(MethodHandleDesc desc) {
    DirectMethodHandleDesc direct = (DirectMethodHandleDesc) desc;
    return new MethodHandleConstantValue(
        direct.kind().name(),
        ClassDescNames.toJavaName(direct.owner()),
        direct.methodName(),
        direct.lookupDescriptor(),
        desc.invocationType().descriptorString());
  }

  @Override
  public String constantValueType() {
    return "MethodHandle";
  }

  @Override
  public String storageValue() {
    return "handle:"
        + kind
        + ":"
        + ownerClass
        + "#"
        + methodName
        + lookupDescriptor;
  }

  @Override
  public Map<String, Object> attributes() {
    Map<String, Object> attrs = new LinkedHashMap<>();
    attrs.put("kind", kind);
    attrs.put("ownerClass", ownerClass);
    attrs.put("methodName", methodName);
    attrs.put("lookupDescriptor", lookupDescriptor);
    attrs.put("invocationTypeDescriptor", invocationTypeDescriptor);
    return Map.copyOf(attrs);
  }
}
