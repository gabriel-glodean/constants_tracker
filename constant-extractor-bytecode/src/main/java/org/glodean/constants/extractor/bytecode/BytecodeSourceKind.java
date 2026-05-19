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
}
