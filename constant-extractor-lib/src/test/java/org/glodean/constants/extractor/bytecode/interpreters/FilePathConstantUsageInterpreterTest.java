package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.interpreter.MethodCallContext;
import org.glodean.constants.interpreter.ReceiverKind;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilePathConstantUsageInterpreterTest {

    private final FilePathConstantUsageInterpreter interpreter = new FilePathConstantUsageInterpreter();

    @Test
    void testCanInterpret() {
        assertTrue(interpreter.canInterpret(UsageType.METHOD_INVOCATION_PARAMETER));
        assertFalse(interpreter.canInterpret(UsageType.FIELD_STORE));
        assertFalse(interpreter.canInterpret(UsageType.STATIC_FIELD_STORE));
    }

    @Test
    void testInterpretNioPathsGet() {
        ConstantUsage usage = interpreter.interpret(loc(10), ctx("java/nio/file/Paths", "get"));

        assertEquals(CoreSemanticType.FILE_PATH, usage.semanticType());
        assertEquals(0.95, usage.confidence());
        assertEquals("java/nio/file/Paths", usage.metadata().get("fileClass"));
        assertEquals("get", usage.metadata().get("fileMethod"));
    }

    @Test
    void testInterpretNioPathOf() {
        ConstantUsage usage = interpreter.interpret(loc(15), ctx("java/nio/file/Path", "of"));

        assertEquals(CoreSemanticType.FILE_PATH, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretNioFilesReadString() {
        ConstantUsage usage = interpreter.interpret(loc(20), ctx("java/nio/file/Files", "readString"));

        assertEquals(CoreSemanticType.FILE_PATH, usage.semanticType());
        assertEquals(0.95, usage.confidence());
    }

    @Test
    void testInterpretJavaIoFileConstructor() {
        ConstantUsage usage = interpreter.interpret(loc(25), ctx("java/io/File", "<init>"));

        assertEquals(CoreSemanticType.FILE_PATH, usage.semanticType());
        assertEquals(0.90, usage.confidence());
    }

    @Test
    void testInterpretFileInputStream() {
        ConstantUsage usage = interpreter.interpret(loc(30), ctx("java/io/FileInputStream", "<init>"));

        assertEquals(CoreSemanticType.FILE_PATH, usage.semanticType());
        assertEquals(0.90, usage.confidence());
    }

    @Test
    void testInterpretNonFileMethod() {
        ConstantUsage usage = interpreter.interpret(loc(35), ctx("java/lang/String", "valueOf"));

        assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
        assertEquals(0.0, usage.confidence());
    }

    @Test
    void testInterpretUnknownMethodOnFileClass() {
        ConstantUsage usage = interpreter.interpret(loc(40), ctx("java/io/File", "toString"));

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
        return new UsageLocation("com/example/FileService", "loadConfig", "()V", offset, null);
    }

    private static MethodCallContext ctx(String targetClass, String targetMethod) {
        return new MethodCallContext(targetClass, targetMethod, "(Ljava/lang/String;)V", ReceiverKind.EXTERNAL_OBJECT);
    }
}

