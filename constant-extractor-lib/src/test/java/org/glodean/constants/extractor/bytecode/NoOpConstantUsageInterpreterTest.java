package org.glodean.constants.extractor.bytecode;

import org.glodean.constants.extractor.MethodCallContext;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the NoOpConstantUsageInterpreter.
 */
class NoOpConstantUsageInterpreterTest {

    private final NoOpConstantUsageInterpreter interpreter = new NoOpConstantUsageInterpreter();

    @Test
    void testCanInterpret() {
        // NoOp interpreter doesn't claim to interpret any types
        assertFalse(interpreter.canInterpret(UsageType.METHOD_INVOCATION_PARAMETER));
        assertFalse(interpreter.canInterpret(UsageType.FIELD_STORE));
        assertFalse(interpreter.canInterpret(UsageType.ARITHMETIC_OPERAND));
        assertFalse(interpreter.canInterpret(UsageType.STRING_CONCATENATION_MEMBER));
    }

    @Test
    void testInterpretReturnsUnknown() {
        UsageLocation location = new UsageLocation(
                "com/example/MyClass",
                "someMethod",
                "()V",
                10,
                null
        );

        ConstantUsage usage = interpreter.interpret(location, null);

        assertEquals(UsageType.METHOD_INVOCATION_PARAMETER, usage.structuralType());
        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(location, usage.location());
        assertEquals(0.0, usage.confidence());
        assertTrue(usage.metadata().isEmpty());
    }

    @Test
    void testInterpretIgnoresContext() {
        UsageLocation location = new UsageLocation(
                "com/example/MyClass",
                "anotherMethod",
                "()V",
                null,
                42
        );

        // Create a context (doesn't matter what it is)
        MethodCallContext context = new MethodCallContext(
                "org/slf4j/Logger",
                "info",
                "(Ljava/lang/String;)V"
        );

        ConstantUsage usage = interpreter.interpret(location, context);

        // NoOp should ignore the context and return UNKNOWN
        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(0.0, usage.confidence());
    }

    @Test
    void testMultipleCallsReturnConsistentResults() {
        UsageLocation location = new UsageLocation(
                "com/example/MyClass",
                "method",
                "()V",
                5,
                10
        );

        ConstantUsage usage1 = interpreter.interpret(location, null);
        ConstantUsage usage2 = interpreter.interpret(location, null);

        assertEquals(usage1.structuralType(), usage2.structuralType());
        assertEquals(usage1.semanticType(), usage2.semanticType());
        assertEquals(usage1.confidence(), usage2.confidence());
    }
}

