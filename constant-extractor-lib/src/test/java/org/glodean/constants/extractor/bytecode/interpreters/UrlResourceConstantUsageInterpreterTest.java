package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.interpreter.MethodCallContext;
import org.glodean.constants.interpreter.ReceiverKind;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlResourceConstantUsageInterpreterTest {

    private final UrlResourceConstantUsageInterpreter interpreter = new UrlResourceConstantUsageInterpreter();

    @Test
    void testCanInterpret() {
        assertTrue(interpreter.canInterpret(UsageType.METHOD_INVOCATION_PARAMETER));
        assertFalse(interpreter.canInterpret(UsageType.FIELD_STORE));
        assertFalse(interpreter.canInterpret(UsageType.ARITHMETIC_OPERAND));
    }

    @Test
    void testInterpretJavaNetUrl() {
        ConstantUsage usage = interpreter.interpret(loc(10), ctx("java/net/URL", "<init>"));

        assertEquals(CoreSemanticType.URL_RESOURCE, usage.semanticType());
        assertEquals(0.95, usage.confidence());
        assertEquals("java/net/URL", usage.metadata().get("urlClass"));
        assertEquals("<init>", usage.metadata().get("urlMethod"));
    }

    @Test
    void testInterpretJavaNetUri() {
        ConstantUsage usage = interpreter.interpret(loc(15), ctx("java/net/URI", "create"));

        assertEquals(CoreSemanticType.URL_RESOURCE, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretHttpRequestBuilder() {
        ConstantUsage usage = interpreter.interpret(loc(20), ctx("java/net/http/HttpRequest", "newBuilder"));

        assertEquals(CoreSemanticType.URL_RESOURCE, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretApacheHttpGet() {
        ConstantUsage usage = interpreter.interpret(loc(25), ctx("org/apache/http/client/methods/HttpGet", "<init>"));

        assertEquals(CoreSemanticType.URL_RESOURCE, usage.semanticType());
        assertEquals(0.85, usage.confidence());
    }

    @Test
    void testInterpretSpringRestTemplate() {
        ConstantUsage usage = interpreter.interpret(loc(30), ctx("org/springframework/web/client/RestTemplate", "getForObject"));

        assertEquals(CoreSemanticType.URL_RESOURCE, usage.semanticType());
        assertEquals(0.85, usage.confidence());
    }

    @Test
    void testInterpretOkHttp() {
        ConstantUsage usage = interpreter.interpret(loc(35), ctx("okhttp3/Request$Builder", "url"));

        assertEquals(CoreSemanticType.URL_RESOURCE, usage.semanticType());
        assertEquals(0.85, usage.confidence());
    }

    @Test
    void testInterpretNonUrlMethod() {
        ConstantUsage usage = interpreter.interpret(loc(40), ctx("java/lang/String", "valueOf"));

        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(0.0, usage.confidence());
    }

    @Test
    void testInterpretWithoutContext() {
        ConstantUsage usage = interpreter.interpret(loc(45), null);

        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(0.0, usage.confidence());
    }

    private static UsageLocation loc(int offset) {
        return new UsageLocation("com/example/HttpClient", "fetch", "()V", offset, null);
    }

    private static MethodCallContext ctx(String targetClass, String targetMethod) {
        return new MethodCallContext(targetClass, targetMethod, "(Ljava/lang/String;)V", ReceiverKind.EXTERNAL_OBJECT);
    }
}

