package org.glodean.constants.extractor;

import java.io.IOException;
import java.util.Collection;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;

/**
 * Core interface for extracting constant usage information from Java class files.
 *
 * <p>Implementations analyze bytecode to discover constants (strings, numbers, class references)
 * and classify how they're used throughout the code. This supports use cases like:
 * <ul>
 *   <li>Migration analysis (finding hardcoded values that should be externalized)</li>
 *   <li>API evolution tracking (comparing constant usage across JDK versions)</li>
 *   <li>Security auditing (identifying SQL fragments, URLs, file paths)</li>
 * </ul>
 *
 * @see UnitConstants
 */
public interface ModelExtractor {
  /**
   * Backwards-compatible no-arg extract method used by callers that don't supply an explicit
   * {@link org.glodean.constants.model.UnitDescriptor}. Implementations should provide a
   * meaningful default when possible (e.g. derive class name from parsed model).
   */
  default Collection<UnitConstants> extract() throws ExtractionException {
    throw new UnsupportedOperationException("No default source descriptor available");
  }
  /**
   * Extracts constant usage information from the configured source.
   *
   * @return collection of {@link UnitConstants}, typically one per analyzed class
   * @throws ExtractionException if bytecode parsing or analysis fails
   */
  Collection<UnitConstants> extract(UnitDescriptor source) throws ExtractionException;

  /** Exception type representing extraction failures. */
  class ExtractionException extends IOException {

    public ExtractionException(Throwable cause) {
      super(cause);
    }

    public ExtractionException(String message) {
      super(message);
    }
  }
}
