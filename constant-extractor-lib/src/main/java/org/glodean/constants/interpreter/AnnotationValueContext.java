package org.glodean.constants.interpreter;

import java.util.Objects;

/**
 * Interpretation context for a constant that appears as an annotation element value.
 *
 * <p>Covers annotation values on classes, methods, fields, and parameters.
 * The {@code annotationDescriptor} uses JVM internal format
 * (e.g., {@code "Lorg/springframework/web/bind/annotation/RequestMapping;"}).
 *
 * @param annotationDescriptor JVM type descriptor of the annotation
 *     (e.g., {@code "Ljakarta/persistence/Table;"})
 * @param elementName the annotation element name (e.g., {@code "value"}, {@code "name"})
 * @param targetKind where the annotation is applied
 *
 * @see TargetKind
 * @see ConstantUsageInterpreter.InterpretationContext
 */
public record AnnotationValueContext(
    String annotationDescriptor,
    String elementName,
    TargetKind targetKind
) implements ConstantUsageInterpreter.InterpretationContext {

  /**
   * Describes what program element the annotation is attached to.
   */
  public enum TargetKind {
    /** Annotation on a class, interface, or enum declaration. */
    CLASS,
    /** Annotation on a method or constructor. */
    METHOD,
    /** Annotation on a field. */
    FIELD,
    /** Annotation on a method parameter. */
    PARAMETER
  }

  /**
   * Validates the record state on construction.
   *
   * @throws NullPointerException if any parameter is {@code null}
   */
  public AnnotationValueContext {
    Objects.requireNonNull(annotationDescriptor, "annotationDescriptor cannot be null");
    Objects.requireNonNull(elementName, "elementName cannot be null");
    Objects.requireNonNull(targetKind, "targetKind cannot be null");
  }
}

