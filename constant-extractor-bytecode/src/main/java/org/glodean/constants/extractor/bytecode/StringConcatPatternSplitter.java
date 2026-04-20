package org.glodean.constants.extractor.bytecode;

import java.util.Set;
import java.util.function.Function;

/**
 * Pluggable splitter interface used to extract literal parts from a string-concat pattern.
 *
 * <p>Modern Java compilers (JDK 9+) use {@code invokedynamic} for string concatenation,
 * encoding the concatenation pattern as a recipe string (e.g., {@code "\u0001 + \u0001"}
 * represents {@code var1 + var2}). This interface allows extracting the literal string
 * parts from such patterns.
 *
 * <p><b>Example:</b> For the Java code {@code "Hello " + name + "!"}:
 * <ul>
 *   <li>Pattern string: {@code "Hello \u0001!"}</li>
 *   <li>Splitter output: {@code {"Hello ", "!"}}</li>
 * </ul>
 *
 * <p>Implementations must handle JVM-specific encoding formats. The default implementation
 * is {@link InternalStringConcatPatternSplitter}.
 *
 * @see InternalStringConcatPatternSplitter
 * @see AnalysisMerger
 */
public interface StringConcatPatternSplitter extends Function<String, Set<String>> {}
