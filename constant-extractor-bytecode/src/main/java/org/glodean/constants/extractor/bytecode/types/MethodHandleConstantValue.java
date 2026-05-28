package org.glodean.constants.extractor.bytecode.types;

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.util.LinkedHashMap;
import java.util.Map;
import org.glodean.constants.extractor.bytecode.Utils;

/**
 * Structured representation for a JVM method-handle constant.
 *
 * <p>All class-name and descriptor fields use Java dot notation — consistent with
 * {@code UsageLocation.className}. Inner-class separator {@code $} is treated as {@code .}.
 */
public record MethodHandleConstantValue(
    String kind,
    String ownerClass,
    String methodName,
    String lookupDescriptor,
    String invocationTypeDescriptor
) implements StructuredConstantValue {

  public static MethodHandleConstantValue from(MethodHandleDesc desc) {
    DirectMethodHandleDesc direct = (DirectMethodHandleDesc) desc;
    return new MethodHandleConstantValue(
        direct.kind().name(),
        Utils.toJavaName(direct.owner()),
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
        + Utils.toJavaDescriptor(lookupDescriptor);
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
