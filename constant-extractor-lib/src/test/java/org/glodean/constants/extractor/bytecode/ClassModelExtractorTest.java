package org.glodean.constants.extractor.bytecode;

import static org.glodean.constants.extractor.bytecode.TestUtils.convertClassToModel;
import static org.glodean.constants.model.ClassConstant.CoreSemanticType.UNKNOWN;
import static org.glodean.constants.model.ClassConstant.UsageType.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstants;
import org.glodean.constants.samples.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

@DisplayName("ClassModelExtractor Tests")
class ClassModelExtractorTest {

  private AnalysisMerger analysisMerger;

  @BeforeEach
  void setUp() {
    analysisMerger = new AnalysisMerger(new InternalStringConcatPatternSplitter());
  }

  /**
   * Creates a ConstantUsage with the given structural type and default values.
   * Helper method to reduce boilerplate in test assertions.
   *
   * @param usageType the structural usage type
   * @return a ConstantUsage with default semantic type and location
   */
  private ConstantUsage usage(ClassConstant.UsageType usageType) {
    return new ConstantUsage(
        usageType,
        UNKNOWN,
        new UsageLocation("test/class", "<multiple>", "()V", 0, null),
        0.5);
  }

  /**
   * Creates a ClassConstant with the given value and usage types.
   * Helper method to simplify test assertions.
   *
   * @param value the constant value
   * @param usageTypes the structural usage types
   * @return a ClassConstant with the specified value and usages
   */
  private ClassConstant constant(Object value, ClassConstant.UsageType... usageTypes) {
    Set<ConstantUsage> usages = new HashSet<>();
    for (var usageType : usageTypes) {
      usages.add(usage(usageType));
    }
    return new ClassConstant(value, usages);
  }

  /**
   * Extracts constants from a class and returns the first ClassConstants result.
   *
   * @param classModel the class model to analyze
   * @return the extracted ClassConstants
   * @throws IOException if extraction fails
   */
  private ClassConstants extractConstants(ClassModel classModel) throws IOException {
    var results = new ClassModelExtractor(classModel, analysisMerger).extract();
    var model = results.iterator().hasNext() ? results.iterator().next() : null;
    assertNotNull(model, "Expected extraction to return a non-null result");
    return model;
  }

  /**
   * Convenience method to extract constants from a Java class.
   *
   * @param clazz the Java class to analyze
   * @return the extracted ClassConstants
   * @throws IOException if extraction fails
   */
  private ClassConstants extractConstants(Class<?> clazz) throws IOException {
    return extractConstants(convertClassToModel(clazz));
  }

  /**
   * Asserts that the extracted constants match the expected constants.
   * Compares by value and structural usage types, ignoring location details.
   *
   * @param actual the actual ClassConstants
   * @param expectedClassName the expected class name
   * @param expectedConstants the expected set of constants
   */
  private void assertExtractedConstants(
      ClassConstants actual, String expectedClassName, Set<ClassConstant> expectedConstants) {
    assertEquals(expectedClassName, actual.name(), "Class name mismatch");
    assertEquals(expectedConstants.size(), actual.constants().size(), "Number of constants mismatch");

    // For each expected constant, verify it exists in actual with matching usage types
    for (ClassConstant expectedConstant : expectedConstants) {
      var matchingActual = actual.constants().stream()
          .filter(c -> Objects.equals(c.value(), expectedConstant.value()))
          .findFirst()
          .orElseThrow(() -> new AssertionError(
              "Expected constant not found: " + expectedConstant.value()));

      // Extract structural usage types from both
      Set<ClassConstant.UsageType> expectedUsageTypes = expectedConstant.usages().stream()
          .map(ConstantUsage::structuralType)
          .collect(java.util.stream.Collectors.toSet());
      Set<ClassConstant.UsageType> actualUsageTypes = matchingActual.usages().stream()
          .map(ConstantUsage::structuralType)
          .collect(java.util.stream.Collectors.toSet());

      assertEquals(expectedUsageTypes, actualUsageTypes,
          "Usage types mismatch for constant: " + expectedConstant.value());
    }
  }

  @Nested
  @DisplayName("Integer Constant Extraction Tests")
  class IntegerConstantTests {

    @Test
    @DisplayName("Should extract integer constants used in arithmetic operations")
    void extractSimpleIntegers() throws IOException {
      var actual = extractConstants(SimpleIteration.class);

      assertExtractedConstants(
          actual,
          "org/glodean/constants/samples/SimpleIteration",
          Set.of(
              constant(0, ARITHMETIC_OPERAND),
              constant(1, ARITHMETIC_OPERAND)));
    }
  }

  @Nested
  @DisplayName("String Constant Extraction Tests")
  class StringConstantTests {

    @Test
    @DisplayName("Should extract string constants from method invocations and field stores")
    void extractGreeter() throws IOException {
      var actual = extractConstants(Greeter.class);

      assertExtractedConstants(
          actual,
          "org/glodean/constants/samples/Greeter",
          Set.of(
              constant("Default", METHOD_INVOCATION_PARAMETER),
              constant(Greeter.FORMAT, METHOD_INVOCATION_TARGET),
              constant(Greeter.wackyFormat, STATIC_FIELD_STORE)));
    }

    @Test
    @DisplayName("Should extract string constants from invoke dynamic operations")
    void extractForInvokeDynamic() throws IOException {
      var actual = extractConstants(InvokeDynamicFunctionality.class);

      assertExtractedConstants(
          actual,
          "org/glodean/constants/samples/InvokeDynamicFunctionality",
          Set.of(constant("", METHOD_INVOCATION_PARAMETER, STRING_CONCATENATION_MEMBER)));
    }

    @Test
    @DisplayName("Should extract string constants from exception handling code")
    void extractForThrowingMethods() throws IOException {
      var actual = extractConstants(ThrowingMethodSample.class);

      assertExtractedConstants(
          actual,
          "org/glodean/constants/samples/ThrowingMethodSample",
          Set.of(
              constant("C:\\non_existent_file.txt", METHOD_INVOCATION_PARAMETER),
              constant("", STRING_CONCATENATION_MEMBER),
              constant("Caught exception: ", STRING_CONCATENATION_MEMBER)));
    }
  }

  @Nested
  @DisplayName("Control Flow Constant Extraction Tests")
  class ControlFlowConstantTests {

    @Test
    @DisplayName("Should extract constants from goto/continue statements")
    void extractForContinues() throws IOException {
      var actual = extractConstants(GotoSample.class);

      assertExtractedConstants(
          actual,
          "org/glodean/constants/samples/GotoSample",
          Set.of(
              constant(0, METHOD_INVOCATION_PARAMETER, ARITHMETIC_OPERAND),
              constant(1, METHOD_INVOCATION_PARAMETER, ARITHMETIC_OPERAND),
              constant(2, ARITHMETIC_OPERAND),
              constant(3, METHOD_INVOCATION_PARAMETER),
              constant(10, METHOD_INVOCATION_PARAMETER)));
    }

    @Test
    @DisplayName("Should handle switch statements without extracting constants")
    void extractForSwitchMethods() throws IOException {
      var actual = extractConstants(SwitchFunctionality.class);

      assertExtractedConstants(
          actual, "org/glodean/constants/samples/SwitchFunctionality", Set.of());
    }
  }

  @Nested
  @DisplayName("Synchronization Constant Extraction Tests")
  class SynchronizationConstantTests {

    @Test
    @DisplayName("Should extract constants from synchronized blocks")
    void extractForMonitorMethods() throws IOException {
      var actual = extractConstants(SyncSample.class);

      assertExtractedConstants(
          actual,
          "org/glodean/constants/samples/SyncSample",
          Set.of(
              constant(1L, STATIC_FIELD_STORE, ARITHMETIC_OPERAND),
              constant(0L, STATIC_FIELD_STORE)));
    }
  }

  @Nested
  @DisplayName("Stack Operation Tests")
  class StackOperationTests {

    @Test
    @DisplayName("Should handle valid stack operations")
    void extractForStackOperations() throws IOException {
      var actual = extractConstants(ClassFile.of().parse(StackOperationsGenerator.generateClass()));

      assertExtractedConstants(actual, StackOperationsGenerator.CLASS_NAME, Set.of());
    }

    static List<Supplier<byte[]>> invalidInputs =
        List.of(
            StackOperationsGenerator::generateClassInvalidDupOnCat2,
            StackOperationsGenerator::generateClassInvalidSwapOnCat2,
            StackOperationsGenerator::generateClassInvalidDupX1OnCat2,
            StackOperationsGenerator::generateClassInvalidDup21OnCat2,
            StackOperationsGenerator::generateClassInvalidStack);

    @ParameterizedTest
    @FieldSource("invalidInputs")
    @DisplayName("Should throw IllegalArgumentException for invalid stack operations")
    void extractForFailingStackOperations(Supplier<byte[]> code) {
      assertThrows(
          IllegalArgumentException.class,
          () -> extractConstants(ClassFile.of().parse(code.get())));
    }
  }

  @Nested
  @DisplayName("Special Operation Tests")
  class SpecialOperationTests {

    @Test
    @DisplayName("Should handle NOP operations")
    void extractForNopOperations() throws IOException {
      var actual = extractConstants(ClassFile.of().parse(NopGenerator.generateNop()));

      assertExtractedConstants(actual, NopGenerator.CLASS_NAME, Set.of());
    }

    @Test
    @DisplayName("Should throw UnsupportedOperationException for discontinued JSR operations")
    void extractForDiscontinuedOperations() throws IOException {
      byte[] clazz = Files.readAllBytes(Path.of("src/test/resources/samples/JsrExample.class"));

      assertThrows(
          UnsupportedOperationException.class,
          () -> extractConstants(ClassFile.of().parse(clazz)));
    }

    @Test
    @DisplayName("Should handle type conversion methods")
    void extractForConversionMethods() throws IOException {
      var actual = extractConstants(ConversionFunctionality.class);

      assertExtractedConstants(
          actual, "org/glodean/constants/samples/ConversionFunctionality", Set.of());
    }

    @Test
    @DisplayName("Should handle multi-dimensional array operations")
    void extractForMultiArraysMethods() throws IOException {
      var actual = extractConstants(MultiArrayFunctionality.class);

      assertExtractedConstants(
          actual, "org/glodean/constants/samples/MultiArrayFunctionality", Set.of());
    }
  }
}
