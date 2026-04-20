package org.glodean.constants.extractor.bytecode;

import static org.glodean.constants.extractor.bytecode.TestUtils.convertClassToModel;

import java.io.IOException;
import java.util.stream.Stream;
import org.glodean.constants.samples.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class PointsToJVMTest {

  static class ClassMethodNameProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(
          Arguments.of(SimpleIfElse.class, "<init>", "", false),
          Arguments.of(SimpleIfElse.class, "number", "", false),
          Arguments.of(SimpleIfElse.class, "numberIfElse", "", false),
          Arguments.of(SimpleIteration.class, "sum", "", false),
          Arguments.of(ArrayFunctionality.class, "incrementAndPrintLength", "", false),
          Arguments.of(
              ArrayFunctionality.class,
              "writeToIndex",
              """
                                    8: IASTORE                      \s
                                         IN  Locals: 0=[I@Param#0 1=I@Param#1 2=I@Param#2 | Stack: [[I@Param#0] [I@Param#1] [I@Param#2] | Heap: | Statics: | Arrays:
                                         OUT Locals: 0=[I@Param#0 1=I@Param#1 2=I@Param#2 | Stack: | Heap: | Statics: | Arrays: [I@Param#0[*]=I@Param#2""",
              false),
          Arguments.of(
              ArrayFunctionality.class,
              "readIndex",
              """
                             6: IALOAD                       \s
                                 IN  Locals: 0=[I@Param#0 1=I@Param#1 | Stack: [[I@Param#0] [I@Param#1] | Heap: | Statics: | Arrays:
                                 OUT Locals: 0=[I@Param#0 1=I@Param#1 | Stack: [I@org/glodean/constants/samples/ArrayFunctionality::readIndex([II)I@6] | Heap: | Statics: | Arrays:
                            """,
              false),
          Arguments.of(
              FieldFunctionality.class,
              "<clinit>",
              """
                             2: PUTSTATIC                    \s
                                 IN  Locals: | Stack: [STATIC] | Heap: | Statics: | Arrays:
                                 OUT Locals: | Stack: | Heap: | Statics: org/glodean/constants/samples/FieldFunctionality::staticField Ljava/lang/String;=STATIC | Arrays:
                            """,
              false),
          Arguments.of(
              FieldFunctionality.class,
              "<init>",
              """
                            8: PUTFIELD                     \s
                                 IN  Locals: 0=Lorg/glodean/constants/samples/FieldFunctionality;@org/glodean/constants/samples/FieldFunctionality::<this> | Stack: [Lorg/glodean/constants/samples/FieldFunctionality;@org/glodean/constants/samples/FieldFunctionality::<this>] [Instance] | Heap: | Statics: | Arrays:
                                 OUT Locals: 0=Lorg/glodean/constants/samples/FieldFunctionality;@org/glodean/constants/samples/FieldFunctionality::<this> | Stack: | Heap: Lorg/glodean/constants/samples/FieldFunctionality;@org/glodean/constants/samples/FieldFunctionality::<this>.org/glodean/constants/samples/FieldFunctionality::field Ljava/lang/String;=Instance | Statics: | Arrays:
                            """,
              false),
          Arguments.of(
              FieldFunctionality.class,
              "getStaticField",
              """
                            1: GETSTATIC                    \s
                                 IN  Locals: | Stack: | Heap: | Statics: | Arrays:
                                 OUT Locals: | Stack: [Ljava/lang/String;@org/glodean/constants/samples/FieldFunctionality::getStaticField()Ljava/lang/String;@1] | Heap: | Statics: | Arrays:
                            """,
              false),
          Arguments.of(
              FieldFunctionality.class,
              "getField",
              """
                             4: GETFIELD                     \s
                                 IN  Locals: 0=Lorg/glodean/constants/samples/FieldFunctionality;@org/glodean/constants/samples/FieldFunctionality::<this> | Stack: [Lorg/glodean/constants/samples/FieldFunctionality;@org/glodean/constants/samples/FieldFunctionality::<this>] | Heap: | Statics: | Arrays:
                                 OUT Locals: 0=Lorg/glodean/constants/samples/FieldFunctionality;@org/glodean/constants/samples/FieldFunctionality::<this> | Stack: [Ljava/lang/String;@org/glodean/constants/samples/FieldFunctionality::getField()Ljava/lang/String;@4] | Heap: | Statics: | Arrays:
                            """,
              false),
          Arguments.of(
              TypeCheckingFunctionality.class,
              "isString",
              """
                             5: INSTANCEOF                   \s
                                 IN  Locals: 0=Lorg/glodean/constants/samples/TypeCheckingFunctionality;@org/glodean/constants/samples/TypeCheckingFunctionality::<this> 1=Ljava/lang/Object;@Param#1 | Stack: [Ljava/lang/Object;@Param#1] | Heap: | Statics: | Arrays:
                                 OUT Locals: 0=Lorg/glodean/constants/samples/TypeCheckingFunctionality;@org/glodean/constants/samples/TypeCheckingFunctionality::<this> 1=Ljava/lang/Object;@Param#1 | Stack: [Ljava/lang/String;@org/glodean/constants/samples/TypeCheckingFunctionality::isString(Ljava/lang/Object;)Z@5] | Heap: | Statics: | Arrays:
                            """,
              false),
          Arguments.of(
              TypeCheckingFunctionality.class,
              "stringLength",
              """
                             5: CHECKCAST                    \s
                                 IN  Locals: 0=Lorg/glodean/constants/samples/TypeCheckingFunctionality;@org/glodean/constants/samples/TypeCheckingFunctionality::<this> 1=Ljava/lang/Object;@Param#1 | Stack: [Ljava/lang/Object;@Param#1] | Heap: | Statics: | Arrays:
                            """,
              false));
    }
  }

  @ParameterizedTest
  @ArgumentsSource(ClassMethodNameProvider.class)
  public void run(Class<?> clazz, String methodName, String expectedString, boolean log)
      throws IOException {
    var model = convertClassToModel(clazz);
    var method =
        model.methods().stream()
            .filter(methodModel -> methodModel.methodName().equalsString(methodName))
            .findAny()
            .orElseGet(() -> null);

    Assertions.assertNotNull(method);
    var analysis = new ByteCodeMethodAnalyzer(model, method);
    analysis.run();
    if (log) {
      IO.println(analysis.report());
    }
    Assertions.assertTrue(analysis.report().contains(expectedString));
  }
}
