package org.glodean.constants.extractor;

import org.glodean.constants.model.UnitConstants;

import java.nio.file.Path;
import java.util.Set;

/**
 * SPI for implementations that can extract constants from a given path/resource.
 */
public interface ConstantsExtractor {
    /**
     * Returns true if this extractor can extract constants from the supplied path (or resource).
     */
    boolean supports(Path path);

    /**
     * Extract constants present at the path. For an archive this might return multiple collections.
     */
    Set<UnitConstants> extract(Path path) throws ExtractionException;
}
