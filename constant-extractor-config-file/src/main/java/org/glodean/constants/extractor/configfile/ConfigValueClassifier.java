// ConfigValueClassifier.java
package org.glodean.constants.extractor.configfile;

import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.SemanticType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class ConfigValueClassifier {

    private ConfigValueClassifier() {}

    static SemanticType classifyValue(Object value, String keyPath) {
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

    static UnitConstant toUnitConstant(Object value, String key, String fileName, String format) {
        var location = new UnitConstant.UsageLocation(fileName, key, format, null, 0);
        var semanticType = classifyValue(value, key);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("keyPath", key);
        var usage =
                new UnitConstant.ConstantUsage(UnitConstant.UsageType.FIELD_STORE, semanticType, location, 0.9, metadata);
        return new UnitConstant(value, Set.of(usage));
    }
}
