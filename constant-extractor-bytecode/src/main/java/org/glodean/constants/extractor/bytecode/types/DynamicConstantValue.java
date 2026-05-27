package org.glodean.constants.extractor.bytecode.types;

import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicConstantDesc;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured representation for a JVM CONSTANT_Dynamic (condy) entry.
 *
 * <p>The {@link #constantType()} field uses Java dot notation (e.g. {@code java.lang.Object})
 * — consistent with {@code UsageLocation.className}.
 */
public record DynamicConstantValue(
    String constantName,
    String constantType,
    MethodHandleConstantValue bootstrapMethod,
    List<String> bootstrapArgs
) implements StructuredConstantValue {

  public static DynamicConstantValue from(DynamicConstantDesc<?> desc) {
    List<String> args = desc.bootstrapArgsList().stream()
        .map(DynamicConstantValue::renderConstantDesc)
        .toList();

    return new DynamicConstantValue(
        desc.constantName(),
        ClassDescNames.toJavaName(desc.constantType()),
        MethodHandleConstantValue.from(desc.bootstrapMethod()),
        args);
  }

  private static String renderConstantDesc(ConstantDesc constantDesc) {
    if (constantDesc instanceof DynamicConstantDesc<?> nestedDynamic) {
      return DynamicConstantValue.from(nestedDynamic).storageValue();
    }
    if (constantDesc instanceof java.lang.constant.MethodHandleDesc methodHandleDesc) {
      return MethodHandleConstantValue.from(methodHandleDesc).storageValue();
    }
    if (constantDesc instanceof java.lang.constant.ClassDesc classDesc) {
      return ClassDescNames.toJavaName(classDesc);
    }
    if (constantDesc instanceof java.lang.constant.MethodTypeDesc methodTypeDesc) {
      return methodTypeDesc.descriptorString();
    }
    return String.valueOf(constantDesc);
  }

  @Override
  public String constantValueType() {
    return "DynamicConstant";
  }

  @Override
  public String storageValue() {
    return "condy:"
        + constantName
        + ":"
        + constantType
        + "@"
        + bootstrapMethod.storageValue()
        + "("
        + String.join(",", bootstrapArgs)
        + ")";
  }

  @Override
  public Map<String, Object> attributes() {
    Map<String, Object> attrs = new LinkedHashMap<>();
    attrs.put("constantName", constantName);
    attrs.put("constantType", constantType);
    attrs.put("bootstrapMethod", bootstrapMethod.attributes());
    attrs.put("bootstrapArgs", bootstrapArgs);
    return Map.copyOf(attrs);
  }

}
