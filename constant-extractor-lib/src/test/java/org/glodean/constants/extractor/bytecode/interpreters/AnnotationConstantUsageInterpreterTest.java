package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.interpreter.AnnotationValueContext;
import org.glodean.constants.interpreter.AnnotationValueContext.TargetKind;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationConstantUsageInterpreterTest {

    private final AnnotationConstantUsageInterpreter interpreter =
            new AnnotationConstantUsageInterpreter();

    @Test
    void testCanInterpret() {
        assertTrue(interpreter.canInterpret(UsageType.ANNOTATION_VALUE));
        assertFalse(interpreter.canInterpret(UsageType.METHOD_INVOCATION_PARAMETER));
        assertFalse(interpreter.canInterpret(UsageType.FIELD_STORE));
    }

    @Test
    void testInterpretSpringGetMapping() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("Lorg/springframework/web/bind/annotation/GetMapping;", "value", TargetKind.METHOD));

        assertEquals(UsageType.ANNOTATION_VALUE, usage.structuralType());
        assertEquals(CoreSemanticType.API_ENDPOINT, usage.semanticType());
        assertEquals(0.95, usage.confidence());
        assertEquals("METHOD", usage.metadata().get("targetKind"));
    }

    @Test
    void testInterpretRequestMappingPath() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("Lorg/springframework/web/bind/annotation/RequestMapping;", "path", TargetKind.CLASS));

        assertEquals(CoreSemanticType.API_ENDPOINT, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretJaxRsPath() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("Ljakarta/ws/rs/Path;", "value", TargetKind.CLASS));

        assertEquals(CoreSemanticType.API_ENDPOINT, usage.semanticType());
    }

    @Test
    void testInterpretSpringValue() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("Lorg/springframework/beans/factory/annotation/Value;", "value", TargetKind.FIELD));

        assertEquals(CoreSemanticType.PROPERTY_KEY, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretJpaTableName() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("Ljakarta/persistence/Table;", "name", TargetKind.CLASS));

        assertEquals(CoreSemanticType.SQL_FRAGMENT, usage.semanticType());
        assertEquals(0.85, usage.confidence());
    }

    @Test
    void testInterpretJpaNamedQueryValue() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("Ljakarta/persistence/NamedQuery;", "query", TargetKind.CLASS));

        assertEquals(CoreSemanticType.SQL_FRAGMENT, usage.semanticType());
        assertEquals(0.90, usage.confidence());
    }

    @Test
    void testInterpretRegexPattern() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("Ljakarta/validation/constraints/Pattern;", "regexp", TargetKind.FIELD));

        assertEquals(CoreSemanticType.REGEX_PATTERN, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretUnknownAnnotation() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("Lcom/example/CustomAnnotation;", "value", TargetKind.METHOD));

        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(0.0, usage.confidence());
    }

    @Test
    void testInterpretWithoutContext() {
        ConstantUsage usage = interpreter.interpret(loc(), null);

        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(0.0, usage.confidence());
    }

    @Test
    void testInterpretEndpointNonMatchingElement() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("Lorg/springframework/web/bind/annotation/GetMapping;", "produces", TargetKind.METHOD));

        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
    }

    private static UsageLocation loc() {
        return new UsageLocation("com/example/Controller", "<class>", "()V", 0, null);
    }

    private static AnnotationValueContext ctx(String annDesc, String element, TargetKind kind) {
        return new AnnotationValueContext(annDesc, element, kind);
    }
}

