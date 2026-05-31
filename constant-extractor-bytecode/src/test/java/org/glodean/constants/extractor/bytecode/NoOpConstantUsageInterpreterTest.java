package org.glodean.constants.extractor.bytecode;

import org.glodean.constants.interpreter.MethodCallContext;
import org.glodean.constants.interpreter.ReceiverKind;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the NoOpConstantUsageInterpreter.
 */
class NoOpConstantUsageInterpreterTest {

    private final ConstantUsageInterpreterRegistry.NoOpConstantUsageInterpreter interpreter = ConstantUsageInterpreterRegistry.NoOpConstantUsageInterpreter.METHOD_INVOCATION_PARAMETER_INTERPRETER;

    @Test
    void testInterpretReturnsUnknown() {
        UsageLocation location = new UsageLocation(
                "com.example.MyClass",
                "someMethod",
                "()void",
                10,
                null
        );

        ConstantUsage usage = interpreter.interpret(location, new MethodCallContext(
                "com.example.OtherClass",
                "otherMethod",
                "(java.lang.String)void",
                ReceiverKind.EXTERNAL_OBJECT
        ));
        assertNotNull(usage);

        assertEquals(UsageType.METHOD_INVOCATION_PARAMETER, usage.structuralType());
        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(location, usage.location());
        assertEquals(0.0, usage.confidence());
        assertFalse(usage.metadata().isEmpty());
    }

    @Test
    void testInterpretIgnoresContext() {
        UsageLocation location = new UsageLocation(
                "com.example.MyClass",
                "anotherMethod",
                "()void",
                null,
                42
        );

        // Create a context (doesn't matter what it is)
        MethodCallContext context = new MethodCallContext(
                "org.slf4j.Logger",
                "info",
                "(java.lang.String)void",
                ReceiverKind.EXTERNAL_OBJECT
        );

        ConstantUsage usage = interpreter.interpret(location, context);
        assertNotNull(usage);

        // NoOp should ignore the context and return UNKNOWN
        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(0.0, usage.confidence());
    }

    @Test
    void testMultipleCallsReturnConsistentResults() {
        UsageLocation location = new UsageLocation(
                "com.example.MyClass",
                "method",
                "()void",
                5,
                10
        );

        var context = new MethodCallContext(
                "com.example.OtherClass",
                "otherMethod",
                "(java.lang.String)void",
                ReceiverKind.EXTERNAL_OBJECT
        );
        ConstantUsage usage1 = interpreter.interpret(location, context);
        ConstantUsage usage2 = interpreter.interpret(location, context);
        assertNotNull(usage1);
        assertNotNull(usage2);

        assertEquals(usage1.structuralType(), usage2.structuralType());
        assertEquals(usage1.semanticType(), usage2.semanticType());
        assertEquals(usage1.confidence(), usage2.confidence());
    }
}
