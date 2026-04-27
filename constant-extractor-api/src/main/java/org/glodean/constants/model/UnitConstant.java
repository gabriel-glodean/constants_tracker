package org.glodean.constants.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a discovered constant value for a generic unit (class file, properties file, etc.)
 * along with its observed usages.
 *
 * This record replaces the older {@code ClassConstant} and supports constants coming from
 * different unit types such as property files. The record name was changed to {@code UnitConstant}
 * to reflect the broader applicability.
 *
 * @param value the constant value (String, Number, etc.)
 * @param usages detailed usage contexts for this constant
 */
public record UnitConstant(Object value, Set<ConstantUsage> usages) {
    public UnitConstant{
        Objects.requireNonNull(value, "Constant value cannot be null");
        Objects.requireNonNull(usages, "Usages set cannot be null");
    }
    /**
     * Structural usage type - HOW the constant is used in bytecode.
     * Fixed set based on JVM instruction semantics.
     */
    public enum UsageType {
        ARITHMETIC_OPERAND,
        STRING_CONCATENATION_MEMBER,
        STATIC_FIELD_STORE,
        FIELD_STORE,
        METHOD_INVOCATION_TARGET,
        METHOD_INVOCATION_PARAMETER,
        ANNOTATION_VALUE
    }

    /**
     * Semantic classification - WHAT the constant represents.
     * Extensible through custom implementations.
     */
    public sealed interface SemanticType permits
            CoreSemanticType,
            CustomSemanticType {
        String category();
        String displayName();
        default String description() { return ""; }
    }

    public enum CoreSemanticType implements SemanticType {
        SQL_FRAGMENT("database", "SQL Fragment", "SQL query or statement"),
        URL_RESOURCE("network", "URL Resource", "HTTP/HTTPS URL"),
        FILE_PATH("filesystem", "File Path", "Filesystem path"),
        PROPERTY_KEY("config", "Property Key", "Configuration property name"),
        REGEX_PATTERN("text", "Regex Pattern", "Regular expression"),
        LOG_MESSAGE("logging", "Log Message", "Log message template"),
        ERROR_MESSAGE("error", "Error Message", "Error or exception message"),
        CONFIGURATION_VALUE("config", "Configuration Value", "Configuration value"),
        API_ENDPOINT("network", "API Endpoint", "REST API endpoint path"),
        DATE_FORMAT("datetime", "Date Format", "Date/time format string"),
        ENCODING_NAME("text", "Encoding Name", "Character encoding name"),
        MIME_TYPE("network", "MIME Type", "MIME type identifier"),
        HTML_CONTENT("web", "HTML Content", "HTML markup"),
        JSON_KEY("data", "JSON Key", "JSON object key"),
        UNKNOWN("unknown", "Unknown", "Could not classify");

        private final String category;
        private final String displayName;
        private final String description;

        CoreSemanticType(String category, String displayName, String description) {
            this.category = category;
            this.displayName = displayName;
            this.description = description;
        }

        @Override
        public String category() { return category; }

        @Override
        public String displayName() { return displayName; }

        @Override
        public String description() { return description; }
    }

    public record CustomSemanticType(
            String category,
            String displayName,
            String description
    ) implements SemanticType {
        public CustomSemanticType{
            Objects.requireNonNull(category, "Category cannot be null");
            Objects.requireNonNull(displayName, "Display name cannot be null");
            Objects.requireNonNull(description, "Description cannot be null");
        }

        public CustomSemanticType(String category, String displayName) {
            this(category, displayName, "");
        }
    }

    public record ConstantUsage(
            UsageType structuralType,
            SemanticType semanticType,
            UsageLocation location,
            double confidence,
            Map<String, Object> metadata
    ) {
        public ConstantUsage{
            Objects.requireNonNull(structuralType, "Structural type cannot be null");
            Objects.requireNonNull(semanticType, "Semantic type cannot be null");
            Objects.requireNonNull(location, "Location cannot be null");
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
            }
            Objects.requireNonNull(metadata, "Metadata map cannot be null");
        }

        public ConstantUsage(
                UsageType structuralType,
                SemanticType semanticType,
                UsageLocation location,
                double confidence
        ) {
            this(structuralType, semanticType, location, confidence, Collections.emptyMap());
        }
    }

    public record UsageLocation(
            String className,
            String methodName,
            String methodDescriptor,
            Integer bytecodeOffset,
            Integer lineNumber
    ) {
        public UsageLocation{
            Objects.requireNonNull(className, "Class name cannot be null");
            Objects.requireNonNull(methodName, "Method name cannot be null");
            Objects.requireNonNull(methodDescriptor, "Method descriptor cannot be null");
            if (!((bytecodeOffset != null && bytecodeOffset >= 0)
                    || (lineNumber != null && lineNumber >= 0))) {
                throw new IllegalArgumentException("Bytecode offset or line number must be provided and non-negative");
            }
        }
    }
}
