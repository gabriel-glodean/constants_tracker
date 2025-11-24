package org.glodean.constants.extractor.bytecode;

import static org.glodean.constants.extractor.bytecode.TestUtils.convertClassToModel;
import static org.glodean.constants.model.ClassConstant.UsageType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstants;
import org.glodean.constants.samples.GotoSample;
import org.glodean.constants.samples.Greeter;
import org.glodean.constants.samples.InvokeDynamicFunctionality;
import org.glodean.constants.samples.SimpleIteration;
import org.junit.jupiter.api.Test;

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
}
