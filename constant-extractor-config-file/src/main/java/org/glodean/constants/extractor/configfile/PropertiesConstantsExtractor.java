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
import java.util.Properties;
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

/**
 * Extracts constants from Java {@code .properties} files.
 *
 * <p>Each key/value pair becomes a {@link UnitConstant} with the property key
 * recorded in the usage metadata.
 */
public record PropertiesConstantsExtractor() implements ConstantsExtractor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".properties");

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
            Properties props = new Properties();
            props.load(in);
            Set<UnitConstant> constants = new LinkedHashSet<>();
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                constants.add(toUnitConstant(value, key, path.toString()));
            }
            long size = Files.size(path);
            var descriptor = new UnitDescriptor(ConfigFileSourceKind.PROPERTIES, path.toString(), size);
            return Set.of(new UnitConstants(descriptor, constants));
        } catch (IOException e) {
            throw new ExtractionException(e);
        }
    }

    private UnitConstant toUnitConstant(String value, String key, String fileName) {
        var location = new UsageLocation(fileName, key, "properties", null, 0);
        var semanticType = classifyValue(value, key);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("keyPath", key);
        var usage =
                new ConstantUsage(UsageType.FIELD_STORE, semanticType, location, 0.9, metadata);
        return new UnitConstant(value, Set.of(usage));
    }

    private UnitConstant.SemanticType classifyValue(String value, String key) {
        if (value.matches("https?://.*")) return CoreSemanticType.URL_RESOURCE;
        if (value.contains("/") && !value.contains(" ")) return CoreSemanticType.FILE_PATH;
        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("url") || lowerKey.contains("uri")) return CoreSemanticType.URL_RESOURCE;
        if (lowerKey.contains("path") || lowerKey.contains("dir")) return CoreSemanticType.FILE_PATH;
        if (lowerKey.contains("pattern") || lowerKey.contains("regex")) return CoreSemanticType.REGEX_PATTERN;
        return CoreSemanticType.CONFIGURATION_VALUE;
    }
}

