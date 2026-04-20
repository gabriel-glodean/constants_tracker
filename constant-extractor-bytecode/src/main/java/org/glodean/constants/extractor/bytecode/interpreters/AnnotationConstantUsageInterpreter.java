package org.glodean.constants.extractor.bytecode.interpreters;
import org.glodean.constants.interpreter.AnnotationValueContext;
import org.glodean.constants.interpreter.ConstantUsageInterpreter;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import java.util.Map;
import java.util.Set;
public class AnnotationConstantUsageInterpreter implements ConstantUsageInterpreter {
    private static final Set<String> ENDPOINT_ANNOTATIONS = Set.of(
            "Lorg/springframework/web/bind/annotation/RequestMapping;",
            "Lorg/springframework/web/bind/annotation/GetMapping;",
            "Lorg/springframework/web/bind/annotation/PostMapping;",
            "Lorg/springframework/web/bind/annotation/PutMapping;",
            "Lorg/springframework/web/bind/annotation/DeleteMapping;",
            "Lorg/springframework/web/bind/annotation/PatchMapping;",
            "Ljakarta/ws/rs/Path;",
            "Ljavax/ws/rs/Path;"
    );
    private static final Set<String> ENDPOINT_ELEMENTS = Set.of("value", "path");
    private static final Set<String> PROPERTY_ANNOTATIONS = Set.of(
            "Lorg/springframework/beans/factory/annotation/Value;",
            "Lorg/springframework/boot/context/properties/ConfigurationProperties;",
            "Ljakarta/inject/Named;",
            "Ljavax/inject/Named;"
    );
    private static final Set<String> SQL_ANNOTATIONS = Set.of(
            "Ljakarta/persistence/Table;",
            "Ljavax/persistence/Table;",
            "Ljakarta/persistence/Column;",
            "Ljavax/persistence/Column;",
            "Ljakarta/persistence/NamedQuery;",
            "Ljavax/persistence/NamedQuery;",
            "Ljakarta/persistence/NamedNativeQuery;",
            "Ljavax/persistence/NamedNativeQuery;"
    );
    private static final Set<String> SQL_QUERY_ELEMENTS = Set.of("query", "value");
    private static final Set<String> SQL_NAME_ELEMENTS = Set.of("name", "catalog", "schema");
    private static final Set<String> REGEX_ANNOTATIONS = Set.of(
            "Ljakarta/validation/constraints/Pattern;",
            "Ljavax/validation/constraints/Pattern;"
    );
    @Override
    public ConstantUsage interpret(UsageLocation location, InterpretationContext context) {
        if (!(context instanceof AnnotationValueContext(String annDesc, String elementName, var targetKind))) {
            return unknown(location);
        }
        if (ENDPOINT_ANNOTATIONS.contains(annDesc) && ENDPOINT_ELEMENTS.contains(elementName)) {
            return usage(location, CoreSemanticType.API_ENDPOINT, 0.95, annDesc, elementName, targetKind);
        }
        if (PROPERTY_ANNOTATIONS.contains(annDesc)) {
            return usage(location, CoreSemanticType.PROPERTY_KEY, 0.95, annDesc, elementName, targetKind);
        }
        if (SQL_ANNOTATIONS.contains(annDesc) && SQL_QUERY_ELEMENTS.contains(elementName)) {
            return usage(location, CoreSemanticType.SQL_FRAGMENT, 0.90, annDesc, elementName, targetKind);
        }
        if (SQL_ANNOTATIONS.contains(annDesc) && SQL_NAME_ELEMENTS.contains(elementName)) {
            return usage(location, CoreSemanticType.SQL_FRAGMENT, 0.85, annDesc, elementName, targetKind);
        }
        if (REGEX_ANNOTATIONS.contains(annDesc) && "regexp".equals(elementName)) {
            return usage(location, CoreSemanticType.REGEX_PATTERN, 0.95, annDesc, elementName, targetKind);
        }
        return unknown(location);
    }
    @Override
    public boolean canInterpret(UsageType type) {
        return type == UsageType.ANNOTATION_VALUE;
    }
    private static ConstantUsage usage(
            UsageLocation location, CoreSemanticType semanticType, double confidence,
            String annotationDescriptor, String elementName,
            AnnotationValueContext.TargetKind targetKind) {
        return new ConstantUsage(
                UsageType.ANNOTATION_VALUE, semanticType, location, confidence,
                Map.of(
                        "annotationDescriptor", annotationDescriptor,
                        "elementName", elementName,
                        "targetKind", targetKind.name()
                )
        );
    }
    private static ConstantUsage unknown(UsageLocation location) {
        return new ConstantUsage(UsageType.ANNOTATION_VALUE, CoreSemanticType.UNKNOWN, location, 0.0);
    }
}
