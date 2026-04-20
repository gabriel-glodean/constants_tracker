package org.glodean.constants.extractor;

/**
 * Runtime exception for extraction failures.
 */
public class ExtractionException extends RuntimeException {
    public ExtractionException(String message, Throwable cause) { super(message, cause); }
    public ExtractionException(String message) { super(message); }
}

