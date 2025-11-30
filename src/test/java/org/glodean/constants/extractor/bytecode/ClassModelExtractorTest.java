package org.glodean.constants.extractor.bytecode;

import static org.glodean.constants.extractor.bytecode.TestUtils.convertClassToModel;
import static org.glodean.constants.model.ClassConstant.UsageType.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Iterables;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstants;
import org.glodean.constants.samples.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

class ClassModelExtractorTest {

  @Test
  void extractSimpleIntegers() throws IOException {
    var model =
        Iterables.getFirst(
            new ClassModelExtractor(
                    convertClassToModel(SimpleIteration.class),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract(),
            null);
    assertNotNull(model);
    var expected =
        new ClassConstants(
            "org/glodean/constants/samples/SimpleIteration",
            Set.of(
                new ClassConstant(0, EnumSet.of(ARITHMETIC_OPERAND)),
                new ClassConstant(1, EnumSet.of(ARITHMETIC_OPERAND))));
    assertEquals(expected, model);
  }

  @Test
  void extractGreeter() throws IOException {
    var model =
        Iterables.getFirst(
            new ClassModelExtractor(
                    convertClassToModel(Greeter.class),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract(),
            null);
    assertNotNull(model);
    var expected =
        new ClassConstants(
            "org/glodean/constants/samples/Greeter",
            Set.of(
                new ClassConstant("Default", EnumSet.of(METHOD_INVOCATION_PARAMETER)),
                new ClassConstant(Greeter.FORMAT, EnumSet.of(METHOD_INVOCATION_TARGET)),
                new ClassConstant(Greeter.wackyFormat, EnumSet.of(STATIC_FIELD_STORE))));
    assertEquals(expected, model);
  }

  @Test
  void extractForInvokeDynamic() throws IOException {
    var model =
        Iterables.getFirst(
            new ClassModelExtractor(
                    convertClassToModel(InvokeDynamicFunctionality.class),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract(),
            null);
    var expected =
        new ClassConstants(
            "org/glodean/constants/samples/InvokeDynamicFunctionality",
            Set.of(
                new ClassConstant(
                    "", EnumSet.of(METHOD_INVOCATION_PARAMETER, STRING_CONCATENATION_MEMBER))));
    assertEquals(expected, model);
  }

  @Test
  void extractForContinues() throws IOException {
    var model =
        Iterables.getFirst(
            new ClassModelExtractor(
                    convertClassToModel(GotoSample.class),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract(),
            null);
    var expected =
        new ClassConstants(
            "org/glodean/constants/samples/GotoSample",
            Set.of(
                new ClassConstant(0, EnumSet.of(METHOD_INVOCATION_PARAMETER, ARITHMETIC_OPERAND)),
                new ClassConstant(1, EnumSet.of(METHOD_INVOCATION_PARAMETER, ARITHMETIC_OPERAND)),
                new ClassConstant(2, EnumSet.of(ARITHMETIC_OPERAND)),
                new ClassConstant(3, EnumSet.of(METHOD_INVOCATION_PARAMETER)),
                new ClassConstant(10, EnumSet.of(METHOD_INVOCATION_PARAMETER))));
    assertEquals(expected, model);
  }

  @Test
  void extractForThrowingMethods() throws IOException {
    var model =
        Iterables.getFirst(
            new ClassModelExtractor(
                    convertClassToModel(ThrowingMethodSample.class),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract(),
            null);
    var expected =
        new ClassConstants(
            "org/glodean/constants/samples/ThrowingMethodSample",
            Set.of(
                new ClassConstant(
                    "C:\\non_existent_file.txt", EnumSet.of(METHOD_INVOCATION_PARAMETER)),
                new ClassConstant("", EnumSet.of(STRING_CONCATENATION_MEMBER)),
                new ClassConstant("Caught exception: ", EnumSet.of(STRING_CONCATENATION_MEMBER))));
    assertEquals(expected, model);
  }

  @Test
  void extractForMonitorMethods() throws IOException {
    var model =
        Iterables.getFirst(
            new ClassModelExtractor(
                    convertClassToModel(SyncSample.class),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract(),
            null);
    var expected =
        new ClassConstants(
            "org/glodean/constants/samples/SyncSample",
            Set.of(
                new ClassConstant(1L, EnumSet.of(STATIC_FIELD_STORE, ARITHMETIC_OPERAND)),
                new ClassConstant(0L, EnumSet.of(STATIC_FIELD_STORE))));
    assertEquals(expected, model);
  }

  @Test
  void extractForStackOperations() throws IOException {
    var model =
        Iterables.getFirst(
            new ClassModelExtractor(
                    ClassFile.of().parse(StackOperationsGenerator.generateClass()),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract(),
            null);
    var expected = new ClassConstants(StackOperationsGenerator.CLASS_NAME, Set.of());
    assertEquals(expected, model);
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
  void extractForFailingStackOperations(Supplier<byte[]> code) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ClassModelExtractor(
                    ClassFile.of().parse(code.get()),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract());
  }

  @Test
  void extractForNopOperations() throws IOException {
    var model =
        Iterables.getFirst(
            new ClassModelExtractor(
                    ClassFile.of().parse(NopGenerator.generateNop()),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract(),
            null);
    var expected = new ClassConstants(NopGenerator.CLASS_NAME, Set.of());
    assertEquals(expected, model);
  }

  @Test
  void extractForDiscontinuedOperations() throws IOException {
    byte[] clazz = Files.readAllBytes(Path.of("src/test/resources/samples/JsrExample.class"));
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            new ClassModelExtractor(
                    ClassFile.of().parse(clazz),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract());
  }

  @Test
  void extractForConversionMethods() throws IOException {
    var model =
        Iterables.getFirst(
            new ClassModelExtractor(
                    convertClassToModel(ConversionFunctionality.class),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract(),
            null);
    var expected =
        new ClassConstants("org/glodean/constants/samples/ConversionFunctionality", Set.of());
    assertEquals(expected, model);
  }

  @Test
  void extractForMultiArraysMethods() throws IOException {
    var model =
        Iterables.getFirst(
            new ClassModelExtractor(
                    convertClassToModel(MultiArrayFunctionality.class),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract(),
            null);
    var expected =
        new ClassConstants("org/glodean/constants/samples/MultiArrayFunctionality", Set.of());
    assertEquals(expected, model);
  }

  @Test
  void extractForSwitchMethods() throws IOException {
    var model =
        Iterables.getFirst(
            new ClassModelExtractor(
                    convertClassToModel(SwitchFunctionality.class),
                    new AnalysisMerger(new InternalStringConcatPatternSplitter()))
                .extract(),
            null);
    var expected =
        new ClassConstants("org/glodean/constants/samples/SwitchFunctionality", Set.of());
    assertEquals(expected, model);
  }
}
