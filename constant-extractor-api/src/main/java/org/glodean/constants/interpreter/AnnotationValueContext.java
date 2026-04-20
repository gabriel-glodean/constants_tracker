package org.glodean.constants.interpreter;

import java.util.Objects;

public record AnnotationValueContext(
    String annotationDescriptor,
    String elementName,
    TargetKind targetKind
) implements ConstantUsageInterpreter.InterpretationContext {

  public enum TargetKind {
    CLASS,
    METHOD,
    FIELD,
    PARAMETER
  }

  public AnnotationValueContext {
    Objects.requireNonNull(annotationDescriptor, "annotationDescriptor cannot be null");
    Objects.requireNonNull(elementName, "elementName cannot be null");
    Objects.requireNonNull(targetKind, "targetKind cannot be null");
  }
}

