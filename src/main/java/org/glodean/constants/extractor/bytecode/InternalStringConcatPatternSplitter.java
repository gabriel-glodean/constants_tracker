package org.glodean.constants.extractor.bytecode;

import java.util.HashSet;
import java.util.Set;

/**
 * Splits a string concatenation pattern using the internal separator character {@code '\u0001'}.
 *
 * <p><b>Risk:</b> This implementation mimics internal JDK string concatenation logic, which may
 * change between Java versions and is not part of the public API. Use with caution and validate
 * against the target JVM version.
 */
public class InternalStringConcatPatternSplitter implements StringConcatPatternSplitter {
  /**
   * Splits the given pattern by the internal separator {@code '\u0001'}. Returns a set of
   * substrings found between separators.
   *
   * <p><b>Note:</b> This method assumes the pattern format used by JDK string concatenation
   * internals. Future JDK changes may break this logic.
   *
   * @param pattern the string pattern to split
   * @return a set of substrings extracted from the pattern
   */
  @Override
  public Set<String> apply(String pattern) {
    Set<String> result = new HashSet<>();
    int start = 0;
    int idx;
    while ((idx = pattern.indexOf('\u0001', start)) != -1) {
      if (start != idx) {
        result.add(pattern.substring(start, idx));
      }
      start = idx + 1;
    }
    if (start < pattern.length()) {
      result.add(pattern.substring(start));
    }
    return result;
  }
}
