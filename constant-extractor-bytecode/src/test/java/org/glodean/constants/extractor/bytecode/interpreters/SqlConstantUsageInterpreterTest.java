package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.interpreter.MethodCallContext;
import org.glodean.constants.interpreter.ReceiverKind;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlConstantUsageInterpreterTest {

    private final SqlConstantUsageInterpreter interpreter = new SqlConstantUsageInterpreter();

    @Test
    void testCanInterpret() {
        assertTrue(interpreter.canInterpret(UsageType.METHOD_INVOCATION_PARAMETER));
        assertFalse(interpreter.canInterpret(UsageType.FIELD_STORE));
        assertFalse(interpreter.canInterpret(UsageType.ARITHMETIC_OPERAND));
    }

    @Test
    void testInterpretJdbcStatement() {
        UsageLocation location = loc(10);
        MethodCallContext context = ctx("java/sql/Statement", "executeQuery");

        ConstantUsage usage = interpreter.interpret(location, context);

        assertEquals(CoreSemanticType.SQL_FRAGMENT, usage.semanticType());
        assertEquals(UsageType.METHOD_INVOCATION_PARAMETER, usage.structuralType());
        assertEquals(0.95, usage.confidence());
        assertEquals("java/sql/Statement", usage.metadata().get("dbClass"));
        assertEquals("executeQuery", usage.metadata().get("dbMethod"));
    }

    @Test
    void testInterpretPreparedStatement() {
        ConstantUsage usage = interpreter.interpret(loc(5), ctx("java/sql/Connection", "prepareStatement"));

        assertEquals(CoreSemanticType.SQL_FRAGMENT, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretJpaEntityManager() {
        ConstantUsage usage = interpreter.interpret(loc(15), ctx("jakarta/persistence/EntityManager", "createQuery"));

        assertEquals(CoreSemanticType.SQL_FRAGMENT, usage.semanticType());
        assertEquals(0.90, usage.confidence());
    }

    @Test
    void testInterpretJavaxEntityManager() {
        ConstantUsage usage = interpreter.interpret(loc(15), ctx("javax/persistence/EntityManager", "createNativeQuery"));

        assertEquals(CoreSemanticType.SQL_FRAGMENT, usage.semanticType());
        assertEquals(0.90, usage.confidence());
    }

    @Test
    void testInterpretSpringJdbcTemplate() {
        ConstantUsage usage = interpreter.interpret(loc(20), ctx("org/springframework/jdbc/core/JdbcTemplate", "queryForObject"));

        assertEquals(CoreSemanticType.SQL_FRAGMENT, usage.semanticType());
        assertEquals(0.85, usage.confidence());
    }

    @Test
    void testInterpretNonSqlMethod() {
        ConstantUsage usage = interpreter.interpret(loc(25), ctx("java/lang/String", "valueOf"));

        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(0.0, usage.confidence());
    }

    @Test
    void testInterpretWithoutContext() {
        ConstantUsage usage = interpreter.interpret(loc(30), null);

        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(0.0, usage.confidence());
    }

    private static UsageLocation loc(int offset) {
        return new UsageLocation("com/example/Dao", "query", "()V", offset, null);
    }

    private static MethodCallContext ctx(String targetClass, String targetMethod) {
        return new MethodCallContext(targetClass, targetMethod, "(Ljava/lang/String;)V", ReceiverKind.EXTERNAL_OBJECT);
    }
}

