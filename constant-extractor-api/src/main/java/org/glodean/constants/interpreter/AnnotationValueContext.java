package org.glodean.constants.interpreter;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;

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

  /** Keys: {@code annotationDescriptor}, {@code elementName}, {@code targetKind}. */
  @Override
  public SequencedMap<String, Object> attributes() {
    LinkedHashMap<String, Object> attrs = new LinkedHashMap<>();
    attrs.put("annotationDescriptor", annotationDescriptor);
    attrs.put("elementName", elementName);
    attrs.put("targetKind", targetKind.name());
    return attrs;
  }
}
