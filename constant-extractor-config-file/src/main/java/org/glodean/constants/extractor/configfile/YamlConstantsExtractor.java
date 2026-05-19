package org.glodean.constants.extractor.configfile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.yaml.snakeyaml.Yaml;

import static org.glodean.constants.extractor.configfile.ConfigValueClassifier.toUnitConstant;

/**
 * Extracts constants from YAML configuration files (*.yml, *.yaml).
 *
 * <p>Each leaf value in the YAML document becomes a {@link UnitConstant} whose key path
 * (e.g. {@code spring.datasource.url}) is recorded in the usage metadata.
 */
public record YamlConstantsExtractor(byte[] content) implements ModelExtractor {

    private static void flattenMap(
            String prefix, Map<?, ?> map, Set<UnitConstant> constants, String filePath, int docIndex) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flattenMap(key, nested, constants, filePath, docIndex);
            } else if (value instanceof Iterable<?> list) {
                int idx = 0;
                for (Object item : list) {
                    String indexedKey = key + "[" + idx + "]";
                    if (item instanceof Map<?, ?> nested) {
                        flattenMap(indexedKey, nested, constants, filePath, docIndex);
                    } else if (item != null) {
                        constants.add(toUnitConstant(item, indexedKey, filePath, "doc#" + docIndex));
                    }
                    idx++;
                }
            } else if (value != null) {
                constants.add(toUnitConstant(value, key, filePath, "doc#" + docIndex));
            }
        }
    }

    @Override
    public Collection<UnitConstants> extract(UnitDescriptor source) throws ModelExtractor.ExtractionException {
        try (InputStream in = new ByteArrayInputStream(content)) {
            Yaml yaml = new Yaml();
            Iterable<Object> documents = yaml.loadAll(in);
            Set<UnitConstant> constants = new LinkedHashSet<>();
            int docIndex = 0;
            for (Object doc : documents) {
                if (doc instanceof Map<?, ?> map) {
                    flattenMap("", map, constants, source.path(), docIndex);
                }
                docIndex++;
            }
            return Set.of(new UnitConstants(source, constants));
        } catch (IOException e) {
            throw new ModelExtractor.ExtractionException(e);
        }
    }
}
