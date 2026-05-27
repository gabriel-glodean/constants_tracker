package org.glodean.constants.extractor.bytecode.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the structured constant value types, covering attributes(),
 * storageValue() shape, and all DynamicConstantValue.renderConstantDesc() branches.
 */
class StructuredConstantValueTest {

  // -------------------------------------------------------------------------
  // ClassDescConstantValue
  // -------------------------------------------------------------------------

  @Test
  void classDescConstantValue_attributes_containsJavaNameAndIsArray() {
    ClassDescConstantValue cv = ClassDescConstantValue.from(ConstantDescs.CD_String);

    Map<String, Object> attrs = cv.attributes();
    assertEquals("java.lang.String", attrs.get("javaName"));
    assertEquals(false, attrs.get("isArray"));
  }

  @Test
  void classDescConstantValue_arrayAttributes_containsIsArrayTrue() {
    ClassDescConstantValue cv = ClassDescConstantValue.from(ClassDesc.ofDescriptor("[I"));

    Map<String, Object> attrs = cv.attributes();
    assertEquals("int[]", attrs.get("javaName"));
    assertEquals(true, attrs.get("isArray"));
  }

  @Test
  void classDescConstantValue_primitiveDesc_returnsJavaName() {
    ClassDescConstantValue cv = ClassDescConstantValue.from(ConstantDescs.CD_int);

    assertEquals("ClassDescriptor", cv.constantValueType()); // primitives are not arrays
    assertEquals("int", cv.javaName());
    assertEquals("int", cv.storageValue());
  }

  // -------------------------------------------------------------------------
  // MethodHandleConstantValue
  // -------------------------------------------------------------------------

  @Test
  void methodHandleConstantValue_attributes_containsExpectedKeys() {
    MethodHandleDesc desc = MethodHandleDesc.ofMethod(
        DirectMethodHandleDesc.Kind.STATIC,
        ConstantDescs.CD_Integer,
        "parseInt",
        MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)I"));

    MethodHandleConstantValue cv = MethodHandleConstantValue.from(desc);
    Map<String, Object> attrs = cv.attributes();

    assertEquals("STATIC", attrs.get("kind"));
    assertEquals("java.lang.Integer", attrs.get("ownerClass"));
    assertEquals("parseInt", attrs.get("methodName"));
    assertNotNull(attrs.get("lookupDescriptor"));
    assertNotNull(attrs.get("invocationTypeDescriptor"));
  }

  @Test
  void methodHandleConstantValue_virtualMethod_ownerClassInDotNotation() {
    MethodHandleDesc desc = MethodHandleDesc.ofMethod(
        DirectMethodHandleDesc.Kind.VIRTUAL,
        ConstantDescs.CD_String,
        "length",
        MethodTypeDesc.ofDescriptor("()I"));

    MethodHandleConstantValue cv = MethodHandleConstantValue.from(desc);

    assertEquals("java.lang.String", cv.ownerClass());
    assertEquals("VIRTUAL", cv.kind());
    assertTrue(cv.storageValue().startsWith("handle:VIRTUAL:java.lang.String#length"));
  }

  @Test
  void methodHandleConstantValue_fieldGetter_storageValueContainsOwner() {
    MethodHandleDesc desc = MethodHandleDesc.ofField(
        DirectMethodHandleDesc.Kind.GETTER,
        ClassDesc.of("com.example.Foo"),
        "bar",
        ConstantDescs.CD_int);

    MethodHandleConstantValue cv = MethodHandleConstantValue.from(desc);

    assertEquals("com.example.Foo", cv.ownerClass());
    assertEquals("GETTER", cv.kind());
    assertTrue(cv.storageValue().contains("com.example.Foo#bar"));
  }

  // -------------------------------------------------------------------------
  // DynamicConstantValue — attributes()
  // -------------------------------------------------------------------------

  @Test
  void dynamicConstantValue_attributes_containsExpectedKeys() {
    DynamicConstantValue cv = DynamicConstantValue.from((DynamicConstantDesc<?>) ConstantDescs.NULL);
    Map<String, Object> attrs = cv.attributes();

    assertEquals("_", attrs.get("constantName"));
    assertEquals("java.lang.Object", attrs.get("constantType"));
    assertNotNull(attrs.get("bootstrapMethod"));
    assertNotNull(attrs.get("bootstrapArgs"));
  }

  @Test
  void dynamicConstantValue_constantType_inDotNotation() {
    DynamicConstantValue cv = DynamicConstantValue.from((DynamicConstantDesc<?>) ConstantDescs.NULL);

    assertFalse(cv.constantType().contains("/"),
        "constantType must use Java dot notation, got: " + cv.constantType());
    assertEquals("java.lang.Object", cv.constantType());
  }

  // -------------------------------------------------------------------------
  // DynamicConstantValue — renderConstantDesc() branches via bootstrap args
  // -------------------------------------------------------------------------

  /** Shared bootstrap method descriptor for building test DynamicConstantDescs. */
  private static DirectMethodHandleDesc testBsm() {
    return MethodHandleDesc.ofMethod(
        DirectMethodHandleDesc.Kind.STATIC,
        ClassDesc.of("com.example.Bootstrap"),
        "bsm",
        MethodTypeDesc.of(
            ConstantDescs.CD_Object,
            ClassDesc.of("java.lang.invoke.MethodHandles$Lookup"),
            ConstantDescs.CD_String,
            ConstantDescs.CD_Class));
  }

  @Test
  void dynamicConstantValue_renderConstantDesc_classDescArg_usesJavaName() {
    // ClassDesc bootstrap arg → renderConstantDesc path 3
    DynamicConstantDesc<?> desc = DynamicConstantDesc.ofNamed(
        testBsm(), "myConst", ConstantDescs.CD_Object, ConstantDescs.CD_String);

    DynamicConstantValue cv = DynamicConstantValue.from(desc);

    // The ClassDesc arg (CD_String) should appear as "java.lang.String" in bootstrapArgs
    assertTrue(cv.bootstrapArgs().contains("java.lang.String"),
        "Expected java.lang.String in bootstrapArgs, got: " + cv.bootstrapArgs());
    assertFalse(cv.bootstrapArgs().stream().anyMatch(a -> a.contains("/")),
        "Bootstrap args must not contain JVM slash notation");
  }

  @Test
  void dynamicConstantValue_renderConstantDesc_methodTypeDescArg_usesDescriptorString() {
    // MethodTypeDesc bootstrap arg → renderConstantDesc path 4
    MethodTypeDesc mtd = MethodTypeDesc.of(ConstantDescs.CD_String, ConstantDescs.CD_int);
    DynamicConstantDesc<?> desc = DynamicConstantDesc.ofNamed(
        testBsm(), "myConst", ConstantDescs.CD_Object, mtd);

    DynamicConstantValue cv = DynamicConstantValue.from(desc);

    assertTrue(cv.bootstrapArgs().stream().anyMatch(a -> a.contains("(")),
        "MethodTypeDesc bootstrap arg should appear as a descriptor string, got: " + cv.bootstrapArgs());
  }

  @Test
  void dynamicConstantValue_renderConstantDesc_methodHandleArg_usesStorageValue() {
    // MethodHandleDesc bootstrap arg → renderConstantDesc path 2
    MethodHandleDesc mhArg = MethodHandleDesc.ofMethod(
        DirectMethodHandleDesc.Kind.STATIC,
        ConstantDescs.CD_Integer,
        "valueOf",
        MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Integer;"));
    DynamicConstantDesc<?> desc = DynamicConstantDesc.ofNamed(
        testBsm(), "myConst", ConstantDescs.CD_Object, mhArg);

    DynamicConstantValue cv = DynamicConstantValue.from(desc);

    assertTrue(cv.bootstrapArgs().stream().anyMatch(a -> a.startsWith("handle:")),
        "MethodHandleDesc arg should start with 'handle:', got: " + cv.bootstrapArgs());
  }

  @Test
  void dynamicConstantValue_renderConstantDesc_nestedDynamicArg_usesStorageValue() {
    // DynamicConstantDesc bootstrap arg → renderConstantDesc path 1
    DynamicConstantDesc<?> desc = DynamicConstantDesc.ofNamed(
        testBsm(), "outer", ConstantDescs.CD_Object, ConstantDescs.NULL);

    DynamicConstantValue cv = DynamicConstantValue.from(desc);

    assertTrue(cv.bootstrapArgs().stream().anyMatch(a -> a.startsWith("condy:")),
        "Nested DynamicConstantDesc arg should start with 'condy:', got: " + cv.bootstrapArgs());
  }

  @Test
  void dynamicConstantValue_renderConstantDesc_primitiveArg_fallsBackToStringValue() {
    // Integer / Long / etc. bootstrap args → renderConstantDesc fallback path
    DynamicConstantDesc<?> desc = DynamicConstantDesc.ofNamed(
        testBsm(), "myConst", ConstantDescs.CD_Object, 42);

    DynamicConstantValue cv = DynamicConstantValue.from(desc);

    assertTrue(cv.bootstrapArgs().contains("42"),
        "Integer bootstrap arg should fall back to String.valueOf(), got: " + cv.bootstrapArgs());
  }
}
