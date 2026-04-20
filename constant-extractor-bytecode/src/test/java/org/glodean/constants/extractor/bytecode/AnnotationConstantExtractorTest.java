package org.glodean.constants.extractor.bytecode;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.glodean.constants.extractor.bytecode.interpreters.BytecodeInterpreterRegistryFactory;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link AnnotationConstantExtractor} correctly extracts constants from annotations
 * placed on classes, methods, fields, and parameters, using bytecode generated at runtime via
 * the ClassFile API.
 */
@DisplayName("AnnotationConstantExtractor integration tests")
class AnnotationConstantExtractorTest {

  private static final ClassDesc CD_OBJECT = ClassDesc.of("java.lang.Object");
  private static final ClassDesc CD_STRING = ClassDesc.of("java.lang.String");

  // Annotation descriptors (as they appear in bytecode)
  private static final ClassDesc ANN_GET_MAPPING =
      ClassDesc.ofDescriptor("Lorg/springframework/web/bind/annotation/GetMapping;");
  private static final ClassDesc ANN_VALUE =
      ClassDesc.ofDescriptor("Lorg/springframework/beans/factory/annotation/Value;");
  private static final ClassDesc ANN_TABLE =
      ClassDesc.ofDescriptor("Ljakarta/persistence/Table;");
  private static final ClassDesc ANN_COLUMN =
      ClassDesc.ofDescriptor("Ljakarta/persistence/Column;");
  private static final ClassDesc ANN_PATTERN =
      ClassDesc.ofDescriptor("Ljakarta/validation/constraints/Pattern;");
  private static final ClassDesc ANN_CUSTOM =
      ClassDesc.ofDescriptor("Lcom/example/CustomAnnotation;");

  /**
   * Builds a class with annotations on the class, a field, a method, and a method parameter,
   * then extracts constants and verifies them.
   */
  @Test
  @DisplayName("Extracts constants from class, field, method, and parameter annotations")
  void extractAnnotationConstants() throws IOException {
    byte[] bytes = ClassFile.of()
        .build(
            ClassDesc.ofInternalName("com/example/AnnotatedController"),
            cb -> {
              cb.withFlags(ACC_PUBLIC).withVersion(69, 0);

              // Class-level: @Table(name = "users", schema = "public")
              cb.with(RuntimeVisibleAnnotationsAttribute.of(
                  java.lang.classfile.Annotation.of(ANN_TABLE, List.of(
                      AnnotationElement.of("name", AnnotationValue.ofString("users")),
                      AnnotationElement.of("schema", AnnotationValue.ofString("public"))
                  ))
              ));

              // Field: @Column(name = "email") @Value("${mail.from}")
              cb.withField("email", CD_STRING, fb ->
                  fb.withFlags(ACC_PUBLIC)
                      .with(RuntimeVisibleAnnotationsAttribute.of(
                          java.lang.classfile.Annotation.of(ANN_COLUMN, List.of(
                              AnnotationElement.of("name", AnnotationValue.ofString("email"))
                          )),
                          java.lang.classfile.Annotation.of(ANN_VALUE, List.of(
                              AnnotationElement.of("value", AnnotationValue.ofString("${mail.from}"))
                          ))
                      ))
              );

              // Method: @GetMapping(value = "/api/users")
              // with parameter: @Pattern(regexp = "\\d+")
              cb.withMethod(
                  "findUsers",
                  MethodTypeDesc.of(CD_OBJECT, CD_STRING),
                  ACC_PUBLIC,
                  mb -> {
                    mb.with(RuntimeVisibleAnnotationsAttribute.of(
                        java.lang.classfile.Annotation.of(ANN_GET_MAPPING, List.of(
                            AnnotationElement.of("value",
                                AnnotationValue.ofArray(
                                    AnnotationValue.ofString("/api/users")
                                ))
                        ))
                    ));
                    mb.with(RuntimeVisibleParameterAnnotationsAttribute.of(
                        List.of(List.of(
                            java.lang.classfile.Annotation.of(ANN_PATTERN, List.of(
                                AnnotationElement.of("regexp", AnnotationValue.ofString("\\d+"))
                            ))
                        ))
                    ));
                    mb.withCode(code -> {
                      code.aconst_null();
                      code.areturn();
                    });
                  });

              // Method with unknown annotation: @CustomAnnotation("hello")
              cb.withMethod(
                  "custom",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb -> {
                    mb.with(RuntimeVisibleAnnotationsAttribute.of(
                        java.lang.classfile.Annotation.of(ANN_CUSTOM, List.of(
                            AnnotationElement.of("value", AnnotationValue.ofString("hello"))
                        ))
                    ));
                    mb.withCode(CodeBuilder::return_);
                  });

              // Method with numeric annotation value
              cb.withMethod(
                  "numeric",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb -> {
                    mb.with(RuntimeVisibleAnnotationsAttribute.of(
                        java.lang.classfile.Annotation.of(ANN_CUSTOM, List.of(
                            AnnotationElement.of("timeout", AnnotationValue.ofInt(5000))
                        ))
                    ));
                    mb.withCode(CodeBuilder::return_);
                  });
            });

    ClassModel model = ClassFile.of().parse(bytes);

    // Use the full registry with the AnnotationConstantUsageInterpreter
    AnalysisMerger merger = new AnalysisMerger(
        new InternalStringConcatPatternSplitter(),
        BytecodeInterpreterRegistryFactory.registry());

    var descriptor = new UnitDescriptor(BytecodeSourceKind.CLASS_FILE, "TestAnnotatedClass");
    var results = new ClassModelExtractor(model, merger).extract(descriptor);
    UnitConstants classConstants = results.iterator().next();

    // Collect all constant values
    Set<Object> values = classConstants.constants().stream()
        .map(UnitConstant::value)
        .collect(Collectors.toSet());

    // All annotation string constants should be extracted
    assertTrue(values.contains("users"), "Expected class-level @Table(name)");
    assertTrue(values.contains("public"), "Expected class-level @Table(schema)");
    assertTrue(values.contains("email"), "Expected field-level @Column(name)");
    assertTrue(values.contains("${mail.from}"), "Expected field-level @Value");
    assertTrue(values.contains("/api/users"), "Expected method-level @GetMapping(value)");
    assertTrue(values.contains("\\d+"), "Expected parameter-level @Pattern(regexp)");
    assertTrue(values.contains("hello"), "Expected custom annotation value");
    assertTrue(values.contains(5000), "Expected numeric annotation value");

    // Verify semantic classifications
    assertSemanticType(classConstants, "users", UnitConstant.CoreSemanticType.SQL_FRAGMENT);
    assertSemanticType(classConstants, "${mail.from}", UnitConstant.CoreSemanticType.PROPERTY_KEY);
    assertSemanticType(classConstants, "/api/users", UnitConstant.CoreSemanticType.API_ENDPOINT);
    assertSemanticType(classConstants, "\\d+", UnitConstant.CoreSemanticType.REGEX_PATTERN);
    assertSemanticType(classConstants, "hello", UnitConstant.CoreSemanticType.UNKNOWN);
  }

  /**
   * Verifies that a constant with the given value has at least one usage with the expected
   * semantic type.
   */
  private void assertSemanticType(
      UnitConstants classConstants, Object value, UnitConstant.CoreSemanticType expected) {
    var constant = classConstants.constants().stream()
        .filter(c -> c.value().equals(value))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No constant with value: " + value));

    boolean found = constant.usages().stream()
        .anyMatch(u -> u.structuralType() == UnitConstant.UsageType.ANNOTATION_VALUE
            && u.semanticType() == expected);
    assertTrue(found,
        "Expected constant '%s' to have ANNOTATION_VALUE usage with %s, but usages were: %s"
            .formatted(value, expected, constant.usages()));
  }
}

