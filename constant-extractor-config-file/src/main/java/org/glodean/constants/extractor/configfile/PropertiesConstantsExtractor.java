package org.glodean.constants.extractor.configfile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;

import static org.glodean.constants.extractor.configfile.ConfigValueClassifier.toUnitConstant;

/**
 * Extracts constants from Java {@code .properties} files.
 *
 * <p>Each key/value pair becomes a {@link UnitConstant} with the property key
 * recorded in the usage metadata.
 */
public record PropertiesConstantsExtractor(byte[] content) implements ModelExtractor {

    @Override
    public Collection<UnitConstants> extract(UnitDescriptor source) throws ModelExtractor.ExtractionException {
        try (InputStream in = new ByteArrayInputStream(content)) {
            Properties props = new Properties();
            props.load(in);
            Set<UnitConstant> constants = new LinkedHashSet<>();
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                constants.add(toUnitConstant(value, key, source.path(), "properties"));
            }
            return Set.of(new UnitConstants(source, constants));
        } catch (IOException e) {
            throw new ModelExtractor.ExtractionException(e);
        }
    }
}
