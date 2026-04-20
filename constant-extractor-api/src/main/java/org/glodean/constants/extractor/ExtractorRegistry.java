package org.glodean.constants.extractor;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Simple registry for available ConstantsExtractor implementations.
 */
public interface ExtractorRegistry {
    void register(ConstantsExtractor extractor);
    Optional<ConstantsExtractor> findFor(Path path);
    List<ConstantsExtractor> all();
}

