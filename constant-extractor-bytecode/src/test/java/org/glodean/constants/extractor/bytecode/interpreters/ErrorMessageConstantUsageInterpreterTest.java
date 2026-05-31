package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.interpreter.MethodCallContext;
import org.glodean.constants.interpreter.ReceiverKind;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorMessageConstantUsageInterpreterTest {

    private final ErrorMessageConstantUsageInterpreter interpreter = new ErrorMessageConstantUsageInterpreter();

    @Test
    void testCanInterpret() {
        assertTrue(interpreter.canInterpret(UsageType.METHOD_INVOCATION_PARAMETER));
        assertFalse(interpreter.canInterpret(UsageType.FIELD_STORE));
        assertFalse(interpreter.canInterpret(UsageType.STRING_CONCATENATION_MEMBER));
    }

    @Test
    void testInterpretIllegalArgumentException() {
        ConstantUsage usage = interpreter.interpret(loc(10), ctx("java.lang.IllegalArgumentException", "<init>"));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.ERROR_MESSAGE, usage.semanticType());
        assertEquals(0.95, usage.confidence());
        assertEquals("java.lang.IllegalArgumentException", usage.metadata().get("errorClass"));
        assertEquals("<init>", usage.metadata().get("errorMethod"));
    }

    @Test
    void testInterpretRuntimeException() {
        ConstantUsage usage = interpreter.interpret(loc(15), ctx("java.lang.RuntimeException", "<init>"));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.ERROR_MESSAGE, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretCustomException() {
        ConstantUsage usage = interpreter.interpret(loc(20), ctx("com.example.MyCustomException", "<init>"));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.ERROR_MESSAGE, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretError() {
        ConstantUsage usage = interpreter.interpret(loc(25), ctx("java.lang.AssertionError", "<init>"));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.ERROR_MESSAGE, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretObjectsRequireNonNull() {
        ConstantUsage usage = interpreter.interpret(loc(30), ctx("java.util.Objects", "requireNonNull"));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.ERROR_MESSAGE, usage.semanticType());
        assertEquals(0.90, usage.confidence());
    }

    @Test
    void testInterpretGuavaPreconditions() {
        ConstantUsage usage = interpreter.interpret(loc(35), ctx("com.google.common.base.Preconditions", "checkArgument"));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.ERROR_MESSAGE, usage.semanticType());
        assertEquals(0.90, usage.confidence());
    }

    @Test
    void testInterpretNonExceptionInit() {
        assertNull(interpreter.interpret(loc(40), ctx("java.lang.StringBuilder", "<init>")));
    }

    @Test
    void testInterpretNonErrorMethod() {
        assertNull(interpreter.interpret(loc(45), ctx("java.lang.String", "valueOf")));
    }

    @Test
    void testInterpretWithoutContext() {
        assertNull(interpreter.interpret(loc(50), null));
    }

    private static UsageLocation loc(int offset) {
        return new UsageLocation("com.example.Service", "validate", "()void", offset, null);
    }

    private static MethodCallContext ctx(String targetClass, String targetMethod) {
        return new MethodCallContext(targetClass, targetMethod, "(java.lang.String)void", ReceiverKind.EXTERNAL_OBJECT);
    }
}
