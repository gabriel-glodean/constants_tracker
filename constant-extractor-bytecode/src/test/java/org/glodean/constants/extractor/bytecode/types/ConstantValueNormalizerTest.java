package org.glodean.constants.extractor.bytecode.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import org.junit.jupiter.api.Test;

class ConstantValueNormalizerTest {

  @Test
  void normalizeWrapsMethodHandleDescriptors() {
    MethodHandleDesc methodHandleDesc = MethodHandleDesc.ofMethod(
        DirectMethodHandleDesc.Kind.STATIC,
        ConstantDescs.CD_Integer,
        "parseInt",
        MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)I"));

    Object normalized = ConstantValueNormalizer.normalize(methodHandleDesc);

    MethodHandleConstantValue methodHandle = assertInstanceOf(MethodHandleConstantValue.class, normalized);
    assertEquals("MethodHandle", methodHandle.constantValueType());
    // ownerClass must be in Java dot notation, not JVM descriptor form
    assertEquals("java.lang.Integer", methodHandle.ownerClass());
    assertTrue(methodHandle.storageValue().contains("handle:STATIC:java.lang.Integer#parseInt"));
  }

  @Test
  void normalizeWrapsDynamicConstants() {
    Object normalized = ConstantValueNormalizer.normalize(ConstantDescs.NULL);

    DynamicConstantValue dynamic = assertInstanceOf(DynamicConstantValue.class, normalized);
    assertEquals("DynamicConstant", dynamic.constantValueType());
    assertTrue(dynamic.storageValue().startsWith("condy:"));
    // constantType must be in Java dot notation
    assertFalse(dynamic.constantType().contains("/"), "constantType should not contain JVM slash: " + dynamic.constantType());
    assertEquals("MethodHandle", dynamic.bootstrapMethod().constantValueType());
  }

  @Test
  void normalizeWrapsClassDescriptors() {
    Object normalized = ConstantValueNormalizer.normalize(ConstantDescs.CD_String);

    ClassDescConstantValue classDesc = assertInstanceOf(ClassDescConstantValue.class, normalized);
    assertEquals("ClassDescriptor", classDesc.constantValueType());
    // javaName must be in Java dot notation
    assertEquals("java.lang.String", classDesc.javaName());
    assertEquals("java.lang.String", classDesc.storageValue());
  }

  @Test
  void normalizeWrapsArrayClassDescriptors() {
    Object normalized = ConstantValueNormalizer.normalize(ClassDesc.ofDescriptor("[I"));

    ClassDescConstantValue classDesc = assertInstanceOf(ClassDescConstantValue.class, normalized);
    assertEquals("ArrayDesc", classDesc.constantValueType());
    assertEquals("int[]", classDesc.javaName());
  }
}
