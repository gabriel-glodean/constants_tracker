package org.glodean.constants.extractor.configfile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.glodean.constants.extractor.ConstantsExtractor;
import org.glodean.constants.extractor.ExtractionException;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.yaml.snakeyaml.Yaml;

/**
 * Extracts constants from YAML configuration files (*.yml, *.yaml).
 *
 * <p>Each leaf value in the YAML document becomes a {@link UnitConstant} whose key path
 * (e.g. {@code spring.datasource.url}) is recorded in the usage metadata.
 */
public record YamlConstantsExtractor() implements ConstantsExtractor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".yml", ".yaml");

    @Override
    public boolean supports(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        String name = path.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    @Override
    public Set<UnitConstants> extract(Path path) throws ExtractionException {
        Objects.requireNonNull(path, "path must not be null");
        if (!supports(path)) {
            return Collections.emptySet();
        }
        try (InputStream in = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Iterable<Object> documents = yaml.loadAll(in);
            Set<UnitConstant> constants = new LinkedHashSet<>();
            int docIndex = 0;
            for (Object doc : documents) {
                if (doc instanceof Map<?, ?> map) {
                    flattenMap("", map, constants, path.toString(), docIndex);
                }
                docIndex++;
            }
            long size = Files.size(path);
            var descriptor = new UnitDescriptor(ConfigFileSourceKind.YAML, path.toString(), size);
            return Set.of(new UnitConstants(descriptor, constants));
        } catch (IOException e) {
            throw new ExtractionException("Failed to extract constants from YAML file: " + path, e);
        }
    }

    private void flattenMap(
            String prefix, Map<?, ?> map, Set<UnitConstant> constants, String fileName, int docIndex) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flattenMap(key, nested, constants, fileName, docIndex);
            } else if (value instanceof Iterable<?> list) {
                int idx = 0;
                for (Object item : list) {
                    String indexedKey = key + "[" + idx + "]";
                    if (item instanceof Map<?, ?> nested) {
                        flattenMap(indexedKey, nested, constants, fileName, docIndex);
                    } else if (item != null) {
                        constants.add(toUnitConstant(item, indexedKey, fileName, docIndex));
                    }
                    idx++;
                }
            } else if (value != null) {
                constants.add(toUnitConstant(value, key, fileName, docIndex));
            }
        }
    }

    private UnitConstant toUnitConstant(Object value, String keyPath, String fileName, int docIndex) {
        var location = new UsageLocation(fileName, keyPath, "doc#" + docIndex, null, 0);
        var semanticType = classifyValue(value, keyPath);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("keyPath", keyPath);
        metadata.put("documentIndex", docIndex);
        var usage =
                new ConstantUsage(UsageType.FIELD_STORE, semanticType, location, 0.9, metadata);
        return new UnitConstant(value, Set.of(usage));
    }

    private UnitConstant.SemanticType classifyValue(Object value, String keyPath) {
        if (value instanceof String s) {
            if (s.matches("https?://.*")) return CoreSemanticType.URL_RESOURCE;
            if (s.contains("/") && !s.contains(" ")) return CoreSemanticType.FILE_PATH;
            if (s.matches("\\w+/\\w+.*")) return CoreSemanticType.MIME_TYPE;
        }
        String lowerKey = keyPath.toLowerCase();
        if (lowerKey.contains("url") || lowerKey.contains("uri")) return CoreSemanticType.URL_RESOURCE;
        if (lowerKey.contains("path") || lowerKey.contains("dir")) return CoreSemanticType.FILE_PATH;
        if (lowerKey.contains("pattern") || lowerKey.contains("regex")) return CoreSemanticType.REGEX_PATTERN;
        if (lowerKey.contains("format") && lowerKey.contains("date")) return CoreSemanticType.DATE_FORMAT;
        return CoreSemanticType.CONFIGURATION_VALUE;
    }
}
