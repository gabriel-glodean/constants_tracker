package org.glodean.constants.extractor.bytecode;

import java.util.Set;
import java.util.function.Function;

/** Pluggable splitter interface used to extract literal parts from a string-concat pattern. */
public interface StringConcatPatternSplitter extends Function<String, Set<String>> {}
