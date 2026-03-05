package org.glodean.constants.extractor.bytecode;

import org.glodean.constants.extractor.MethodCallContext;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the LoggingConstantUsageInterpreter.
 */
class LoggingConstantUsageInterpreterTest {

    private final LoggingConstantUsageInterpreter interpreter = new LoggingConstantUsageInterpreter();

    @Test
    void testCanInterpret() {
        assertTrue(interpreter.canInterpret(UsageType.METHOD_INVOCATION_PARAMETER));
        assertFalse(interpreter.canInterpret(UsageType.FIELD_STORE));
        assertFalse(interpreter.canInterpret(UsageType.ARITHMETIC_OPERAND));
    }

    @Test
    void testInterpretSLF4JLogging() {
        UsageLocation location = new UsageLocation(
                "com/example/MyClass",
                "doSomething",
                "()V",
                10,
                null
        );

        MethodCallContext context = new MethodCallContext(
                "org/slf4j/Logger",
                "info",
                "(Ljava/lang/String;)V"
        );

        ConstantUsage usage = interpreter.interpret(location, context);

        assertEquals(UsageType.METHOD_INVOCATION_PARAMETER, usage.structuralType());
        assertEquals(CoreSemanticType.LOG_MESSAGE, usage.semanticType());
        assertEquals(location, usage.location());
        assertTrue(usage.confidence() >= 0.9, "Expected high confidence for SLF4J logger");
        assertEquals("org/slf4j/Logger", usage.metadata().get("loggerClass"));
        assertEquals("info", usage.metadata().get("loggerMethod"));
    }

    @Test
    void testInterpretLog4j2Logging() {
        UsageLocation location = new UsageLocation(
                "com/example/MyClass",
                "handleError",
                "()V",
                25,
                null
        );

        MethodCallContext context = new MethodCallContext(
                "org/apache/logging/log4j/Logger",
                "error",
                "(Ljava/lang/String;)V"
        );

        ConstantUsage usage = interpreter.interpret(location, context);

        assertEquals(CoreSemanticType.LOG_MESSAGE, usage.semanticType());
        assertTrue(usage.confidence() >= 0.9, "Expected high confidence for Log4j2 logger");
    }

    @Test
    void testInterpretSystemOut() {
        UsageLocation location = new UsageLocation(
                "com/example/MyClass",
                "debug",
                "()V",
                5,
                null
        );

        MethodCallContext context = new MethodCallContext(
                "java/io/PrintStream",
                "println",
                "(Ljava/lang/String;)V"
        );

        ConstantUsage usage = interpreter.interpret(location, context);

        assertEquals(CoreSemanticType.LOG_MESSAGE, usage.semanticType());
        assertTrue(usage.confidence() >= 0.8 && usage.confidence() < 0.9,
                "Expected medium-high confidence for System.out");
    }

    @Test
    void testInterpretCustomLogger() {
        UsageLocation location = new UsageLocation(
                "com/example/MyClass",
                "process",
                "()V",
                15,
                null
        );

        MethodCallContext context = new MethodCallContext(
                "com/mycompany/CustomLogger",
                "debug",
                "(Ljava/lang/String;)V"
        );

        ConstantUsage usage = interpreter.interpret(location, context);

        assertEquals(CoreSemanticType.LOG_MESSAGE, usage.semanticType());
        assertTrue(usage.confidence() >= 0.7 && usage.confidence() < 0.8,
                "Expected medium confidence for custom logger");
    }

    @Test
    void testInterpretNonLoggingMethod() {
        UsageLocation location = new UsageLocation(
                "com/example/MyClass",
                "calculate",
                "()V",
                20,
                null
        );

        MethodCallContext context = new MethodCallContext(
                "java/lang/String",
                "valueOf",
                "(I)Ljava/lang/String;"
        );

        ConstantUsage usage = interpreter.interpret(location, context);

        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(0.0, usage.confidence());
    }

    @Test
    void testInterpretWithoutContext() {
        UsageLocation location = new UsageLocation(
                "com/example/MyClass",
                "test",
                "()V",
                30,
                null
        );

        ConstantUsage usage = interpreter.interpret(location, null);

        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(0.0, usage.confidence());
    }
}

