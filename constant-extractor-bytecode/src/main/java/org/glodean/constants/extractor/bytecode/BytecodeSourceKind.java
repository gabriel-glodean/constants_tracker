package org.glodean.constants.extractor.bytecode;

import org.glodean.constants.model.SourceKind;

/**
 * Source kinds recognized by the bytecode extractor module.
 */
public enum BytecodeSourceKind implements SourceKind {
    /** A single {@code .class} file. */
    CLASS_FILE,
    /** A JAR archive containing class files. */
    JAR,
    /** A ZIP archive that is not a JAR. */
    ZIP,
    /** A directory on the filesystem. */
    DIRECTORY;

    /**
     * Infer a {@code BytecodeSourceKind} from a file path / extension.
     *
     * @param path file path or name (e.g. {@code "com/example/Foo.class"})
     * @return best-matching kind, or {@link #CLASS_FILE} as default
     */
    public static BytecodeSourceKind fromPath(String path) {
        if (path == null) return CLASS_FILE;
        String lower = path.toLowerCase();
        if (lower.endsWith(".jar")) return JAR;
        if (lower.endsWith(".zip")) return ZIP;
        if (lower.endsWith(".class")) return CLASS_FILE;
        return DIRECTORY;
    }
}

