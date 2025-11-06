package org.glodean.constants.extractor;

import org.glodean.constants.model.ClassConstants;

import java.io.IOException;
import java.util.Collection;

public interface ModelExtractor {
    Collection<ClassConstants> extract() throws ExtractionException;
    class ExtractionException extends IOException{

        public ExtractionException(Throwable cause) {
            super(cause);
        }


        public ExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
