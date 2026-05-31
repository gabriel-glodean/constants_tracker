package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.interpreter.AnnotationValueContext;
import org.glodean.constants.interpreter.ConstantUsageInterpreter;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;

import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Interpreter for constants appearing in Java annotations.
 *
 * <p>Classifies annotation values as API endpoints, property keys, SQL fragments, and regex
 * patterns based on the annotation type and element name.
 */
public class AnnotationConstantUsageInterpreter implements ConstantUsageInterpreter {
    private static final Set<String> ENDPOINT_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "jakarta.ws.rs.Path",
            "javax.ws.rs.Path"
    );
    private static final Set<String> ENDPOINT_ELEMENTS = Set.of("value", "path");
    private static final Set<String> PROPERTY_ANNOTATIONS = Set.of(
            "org.springframework.beans.factory.annotation.Value",
            "org.springframework.boot.context.properties.ConfigurationProperties",
            "jakarta.inject.Named",
            "javax.inject.Named"
    );
    private static final Set<String> SQL_ANNOTATIONS = Set.of(
            "jakarta.persistence.Table",
            "javax.persistence.Table",
            "jakarta.persistence.Column",
            "javax.persistence.Column",
            "jakarta.persistence.NamedQuery",
            "javax.persistence.NamedQuery",
            "jakarta.persistence.NamedNativeQuery",
            "javax.persistence.NamedNativeQuery"
    );
    private static final Set<String> SQL_QUERY_ELEMENTS = Set.of("query", "value");
    private static final Set<String> SQL_NAME_ELEMENTS = Set.of("name", "catalog", "schema");
    private static final Set<String> REGEX_ANNOTATIONS = Set.of(
            "jakarta.validation.constraints.Pattern",
            "javax.validation.constraints.Pattern"
    );

    @Override
    public ConstantUsage interpret(UsageLocation location, InterpretationContext context) {
        if (!(context instanceof AnnotationValueContext(String annDesc, String elementName, var targetKind))) {
            return null;
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
        return null;
    }

    @Override
    public boolean canInterpret(UsageType type) {
        return type == UsageType.ANNOTATION_VALUE;
    }

    private static ConstantUsage usage(
            UsageLocation location, CoreSemanticType semanticType, double confidence,
            String annotationDescriptor, String elementName,
            AnnotationValueContext.TargetKind targetKind) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("annotationDescriptor", annotationDescriptor);
        metadata.put("elementName", elementName);
        metadata.put("targetKind", targetKind.name());
        return new ConstantUsage(
                UsageType.ANNOTATION_VALUE, semanticType, location, confidence,
                metadata);
    }
}
