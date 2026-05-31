package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.interpreter.AnnotationValueContext;
import org.glodean.constants.interpreter.AnnotationValueContext.TargetKind;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
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
                ctx("org.springframework.web.bind.annotation.GetMapping", "value", TargetKind.METHOD));
        assertNotNull(usage);
        assertEquals(UsageType.ANNOTATION_VALUE, usage.structuralType());
        assertEquals(CoreSemanticType.API_ENDPOINT, usage.semanticType());
        assertEquals(0.95, usage.confidence());
        assertEquals("METHOD", usage.metadata().get("targetKind"));
    }

    @Test
    void testInterpretRequestMappingPath() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("org.springframework.web.bind.annotation.RequestMapping", "path", TargetKind.CLASS));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.API_ENDPOINT, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretJaxRsPath() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("jakarta.ws.rs.Path", "value", TargetKind.CLASS));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.API_ENDPOINT, usage.semanticType());
    }

    @Test
    void testInterpretSpringValue() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("org.springframework.beans.factory.annotation.Value", "value", TargetKind.FIELD));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.PROPERTY_KEY, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretJpaTableName() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("jakarta.persistence.Table", "name", TargetKind.CLASS));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.SQL_FRAGMENT, usage.semanticType());
        assertEquals(0.85, usage.confidence());
    }

    @Test
    void testInterpretJpaNamedQueryValue() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("jakarta.persistence.NamedQuery", "query", TargetKind.CLASS));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.SQL_FRAGMENT, usage.semanticType());
        assertEquals(0.90, usage.confidence());
    }

    @Test
    void testInterpretRegexPattern() {
        ConstantUsage usage = interpreter.interpret(loc(),
                ctx("jakarta.validation.constraints.Pattern", "regexp", TargetKind.FIELD));
        assertNotNull(usage);
        assertEquals(CoreSemanticType.REGEX_PATTERN, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretUnknownAnnotation() {
        assertNull(interpreter.interpret(loc(),
                ctx("com.example.CustomAnnotation", "value", TargetKind.METHOD)));
    }

    @Test
    void testInterpretWithoutContext() {
        assertNull(interpreter.interpret(loc(), null));
    }

    @Test
    void testInterpretEndpointNonMatchingElement() {
        assertNull(interpreter.interpret(loc(),
                ctx("org.springframework.web.bind.annotation.GetMapping", "produces", TargetKind.METHOD)));
    }

    private static UsageLocation loc() {
        return new UsageLocation("com.example.Controller", "<class>", "()void", 0, null);
    }

    private static AnnotationValueContext ctx(String annDesc, String element, TargetKind kind) {
        return new AnnotationValueContext(annDesc, element, kind);
    }
}
