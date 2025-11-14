package org.glodean.constants.extractor;

import java.io.IOException;
import java.util.Collection;
import org.glodean.constants.model.ClassConstants;

public interface ModelExtractor {
  Collection<ClassConstants> extract() throws ExtractionException;

  class ExtractionException extends IOException {

    public ExtractionException(Throwable cause) {
      super(cause);
    }

    public ExtractionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
