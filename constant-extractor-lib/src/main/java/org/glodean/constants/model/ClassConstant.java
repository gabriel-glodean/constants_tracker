package org.glodean.constants.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a discovered constant value in a class along with its observed usages.
 *
 * @param value the constant value (String, Number, etc.)
 * @param usages detailed usage contexts for this constant
 */
public record ClassConstant(Object value, Set<ConstantUsage> usages) {
    public ClassConstant{
        Objects.requireNonNull(value, "Constant value cannot be null");
        Objects.requireNonNull(usages, "Usages set cannot be null");
    }

    /**
     * Structural usage type - HOW the constant is used in bytecode.
     * Fixed set based on JVM instruction semantics.
     *
     * <p>These classifications describe the bytecode-level usage pattern, independent
     * of semantic meaning. For example, {@code "SELECT * FROM users"} and
     * {@code "Hello, world!"} might both be {@link #METHOD_INVOCATION_PARAMETER},
     * but have different semantic types (SQL vs log message).
     */
    public enum UsageType {
        /** Constant is an operand in arithmetic operations (e.g., {@code x + 5}) */
        ARITHMETIC_OPERAND,

        /** Constant appears in string concatenation (including invokedynamic concat) */
        STRING_CONCATENATION_MEMBER,

        /** Constant is stored to a static field (e.g., {@code MyClass.FIELD = "value"}) */
        STATIC_FIELD_STORE,

        /** Constant is stored to an instance field (e.g., {@code this.field = "value"}) */
        FIELD_STORE,

        /** Constant is the target of a method invocation (rare, for method references) */
        METHOD_INVOCATION_TARGET,

        /** Constant is passed as a parameter to a method call */
        METHOD_INVOCATION_PARAMETER,

        /** Constant appears as an annotation element value (e.g., {@code @Table(name = "users")}) */
        ANNOTATION_VALUE
    }

    /**
     * Semantic classification - WHAT the constant represents.
     * Extensible through custom implementations.
     */
    public sealed interface SemanticType permits
            CoreSemanticType,
            CustomSemanticType {

        /**
         * Category identifier for this semantic type.
         */
        String category();

        /**
         * Human-readable name.
         */
        String displayName();

        /**
         * Optional detailed description.
         */
        default String description() {
            return "";
        }
    }

    /**
     * Built-in semantic types from core analysis.
     *
     * <p>These classifications describe WHAT a constant represents in the domain,
     * inferred from surrounding bytecode context (method calls, exception handlers,
     * package names). Multiple semantic types may apply to the same structural usage.
     *
     * <p>Example: {@code "SELECT * FROM users"} is classified as {@link #SQL_FRAGMENT}
     * based on its usage with {@code PreparedStatement.executeQuery()}.
     */
    public enum CoreSemanticType implements SemanticType {
        /** SQL query or DML statement (detected via JDBC/JPA API usage) */
        SQL_FRAGMENT("database", "SQL Fragment", "SQL query or statement"),

        /** HTTP/HTTPS URL (detected via URL/URI constructor or HttpClient usage) */
        URL_RESOURCE("network", "URL Resource", "HTTP/HTTPS URL"),

        /** Filesystem path (detected via File/Path API usage) */
        FILE_PATH("filesystem", "File Path", "Filesystem path"),

        /** Configuration property key (detected via getProperty, @Value patterns) */
        PROPERTY_KEY("config", "Property Key", "Configuration property name"),

        /** Regular expression pattern (detected via Pattern.compile usage) */
        REGEX_PATTERN("text", "Regex Pattern", "Regular expression"),

        /** Logging message template (detected via Logger.info/debug/warn/error) */
        LOG_MESSAGE("logging", "Log Message", "Log message template"),

        /** Error or exception message (detected via throw statements) */
        ERROR_MESSAGE("error", "Error Message", "Error or exception message"),

        /** Configuration value (detected via @ConfigurationProperties, config files) */
        CONFIGURATION_VALUE("config", "Configuration Value", "Configuration value"),

        /** REST API endpoint path (detected via @RequestMapping, HTTP client usage) */
        API_ENDPOINT("network", "API Endpoint", "REST API endpoint path"),

        /** Date/time format string (detected via SimpleDateFormat, DateTimeFormatter) */
        DATE_FORMAT("datetime", "Date Format", "Date/time format string"),

        /** Character encoding name (detected via Charset.forName, String.getBytes) */
        ENCODING_NAME("text", "Encoding Name", "Character encoding name"),

        /** MIME type identifier (detected via MediaType, Content-Type headers) */
        MIME_TYPE("network", "MIME Type", "MIME type identifier"),

        /** HTML markup content (detected via template engines, response writers) */
        HTML_CONTENT("web", "HTML Content", "HTML markup"),

        /** JSON object key (detected via Jackson, Gson, or JSON-P API usage) */
        JSON_KEY("data", "JSON Key", "JSON object key"),

        /** Could not confidently classify (fallback for low-confidence matches) */
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

    /**
     * Custom semantic type for domain-specific or plugin-contributed classifications.
     *
     * <p>Use this to extend the semantic classification system beyond the built-in
     * {@link CoreSemanticType} values. Examples:
     * <ul>
     *   <li>AWS-specific: ARN identifiers, region names, service endpoints</li>
     *   <li>Framework-specific: Spring property keys, Hibernate HQL queries</li>
     *   <li>Domain-specific: product codes, customer IDs, billing categories</li>
     * </ul>
     *
     * <p>Custom types enable specialized analyzers or plugins to contribute their own
     * classifications while maintaining compatibility with the core analysis engine.
     *
     * @param category logical grouping (e.g., "aws", "spring", "custom")
     * @param displayName human-readable name for UI display
     * @param description optional detailed explanation
     */
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

        /**
         * Convenience constructor that defaults description to empty string.
         *
         * @param category logical grouping identifier
         * @param displayName human-readable name
         */
        public CustomSemanticType(String category, String displayName) {
            this(category, displayName, "");
        }
    }

    /**
     * Detailed context for a single usage of a constant.
     *
     * <p>Represents one observation of a constant value being used in code. A single constant
     * may have multiple usages (e.g., the string {@code "SELECT * FROM users"} might appear
     * in three different methods).
     *
     * <p>The {@code confidence} field indicates analysis certainty:
     * <ul>
     *   <li>0.9-1.0: High confidence (strong pattern match + context)</li>
     *   <li>0.7-0.9: Medium confidence (good pattern or context)</li>
     *   <li>0.5-0.7: Low confidence (weak signals)</li>
     *   <li>0.0-0.5: Very low confidence (uncertain classification)</li>
     * </ul>
     *
     * <p>The {@code metadata} map can store additional analysis-specific data such as:
     * <ul>
     *   <li>Pattern match details (e.g., "regex": "SELECT.*FROM")</li>
     *   <li>Context information (e.g., "jdbcClass": "PreparedStatement")</li>
     *   <li>Domain-specific attributes (e.g., "awsService": "s3")</li>
     * </ul>
     *
     * @param structuralType how the constant is used (bytecode-level)
     * @param semanticType what the constant represents (domain-level)
     * @param location where the usage occurs
     * @param confidence analysis confidence (0.0-1.0, higher = more certain)
     * @param metadata additional context-specific data (immutable)
     */
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

        /**
         * Convenience constructor for usages without custom metadata.
         *
         * @param structuralType how the constant is used
         * @param semanticType what the constant represents
         * @param location where the usage occurs
         * @param confidence analysis confidence (0.0-1.0)
         */
        public ConstantUsage(
                UsageType structuralType,
                SemanticType semanticType,
                UsageLocation location,
                double confidence
        ) {
            this(structuralType, semanticType, location, confidence, Collections.emptyMap());
        }
    }

    /**
     * Location information for constant usage.
     *
     * <p>Identifies where in the bytecode a constant is used. At least one of
     * {@code bytecodeOffset} or {@code lineNumber} must be present.
     *
     * <p>The {@code bytecodeOffset} is the instruction index within the method's bytecode
     * (always available from bytecode analysis). The {@code lineNumber} is the source line
     * (only available if the class was compiled with debug information).
     *
     * <p>The compact constructor validates that at least one location indicator is provided
     * and that values, if present, are non-negative. Invalid inputs result in
     * IllegalArgumentException.
     *
     * @param className fully qualified class name (slash-separated, e.g., "java/lang/String")
     * @param methodName method name (e.g., "toString" or "&lt;init&gt;" for constructors)
     * @param methodDescriptor JVM method descriptor (e.g., "(Ljava/lang/String;)V")
     * @param bytecodeOffset zero-based instruction index within the method (null if unavailable)
     * @param lineNumber source code line number (null if debug info not available)
     */
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
