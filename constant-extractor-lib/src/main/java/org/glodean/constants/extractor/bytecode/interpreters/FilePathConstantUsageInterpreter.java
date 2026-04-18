package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.interpreter.ConstantUsageInterpreter;
import org.glodean.constants.interpreter.MethodCallContext;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;

import java.util.Map;
import java.util.Set;

/**
 * Interprets constants passed to filesystem API methods as {@link CoreSemanticType#FILE_PATH}.
 *
 * <p>Detects usage with {@code java.io.File}, {@code java.nio.file.Path},
 * {@code java.nio.file.Paths}, and {@code java.nio.file.Files}.
 */
public class FilePathConstantUsageInterpreter implements ConstantUsageInterpreter {

    private static final Set<String> PATH_CLASSES = Set.of(
            "java/io/File",
            "java/io/FileInputStream",
            "java/io/FileOutputStream",
            "java/io/FileReader",
            "java/io/FileWriter",
            "java/nio/file/Path",
            "java/nio/file/Paths",
            "java/nio/file/Files",
            "java/nio/file/FileSystems"
    );

    private static final Set<String> PATH_FACTORY_METHODS = Set.of(
            "<init>", "of", "get", "resolve", "resolveSibling",
            "readString", "writeString", "readAllLines",
            "newInputStream", "newOutputStream",
            "newBufferedReader", "newBufferedWriter",
            "createFile", "createDirectory", "createDirectories",
            "delete", "deleteIfExists", "move", "copy"
    );

    @Override
    public ConstantUsage interpret(UsageLocation location, InterpretationContext context) {
        if (!(context instanceof MethodCallContext(String targetClass, String targetMethod, String methodDescriptor, _))) {
            return unknown(location);
        }

        if (isFilePathMethod(targetClass, targetMethod)) {
            double confidence = calculateConfidence(targetClass);
            return new ConstantUsage(
                    UsageType.METHOD_INVOCATION_PARAMETER,
                    CoreSemanticType.FILE_PATH,
                    location,
                    confidence,
                    Map.of(
                            "fileClass", targetClass,
                            "fileMethod", targetMethod,
                            "methodDescriptor", methodDescriptor
                    )
            );
        }

        return unknown(location);
    }

    @Override
    public boolean canInterpret(UsageType type) {
        return type == UsageType.METHOD_INVOCATION_PARAMETER;
    }

    private boolean isFilePathMethod(String targetClass, String targetMethod) {
        return PATH_CLASSES.contains(targetClass) && PATH_FACTORY_METHODS.contains(targetMethod);
    }

    private double calculateConfidence(String targetClass) {
        if (targetClass.startsWith("java/nio/file/")) {
            return 0.95;
        }
        return 0.90;
    }

    private static ConstantUsage unknown(UsageLocation location) {
        return new ConstantUsage(UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.UNKNOWN, location, 0.0);
    }
}

