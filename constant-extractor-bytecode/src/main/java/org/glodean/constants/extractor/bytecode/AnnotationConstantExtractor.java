package org.glodean.constants.extractor.bytecode;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.util.Collection;
import java.util.List;
import java.lang.classfile.Attributes;
import org.glodean.constants.interpreter.AnnotationValueContext;
import org.glodean.constants.interpreter.AnnotationValueContext.TargetKind;
import org.glodean.constants.interpreter.ConstantUsageInterpreter;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;

/**
 * Extracts constant values from annotations on classes, methods, fields, and parameters.
 *
 * <p>Walks {@link RuntimeVisibleAnnotationsAttribute} and
 * {@link RuntimeInvisibleAnnotationsAttribute} on every element of a {@link ClassModel},
 * recursing into nested annotations and array values.
 */
final class AnnotationConstantExtractor {

  private final ConstantUsageInterpreterRegistry registry;

  AnnotationConstantExtractor(ConstantUsageInterpreterRegistry registry) {
    this.registry = registry;
  }

  /**
   * Extracts annotation constants from the entire class model.
   *
   * @param model     the class model to scan
   * @param className dot-separated class name used in {@link UsageLocation}
   * @return multimap of constant values to their usage descriptions
   */
  Multimap<Object, ConstantUsage> extract(ClassModel model, String className) {
    Multimap<Object, ConstantUsage> map = HashMultimap.create();

    // Class-level annotations
    extractAnnotations(
        model.findAttribute(Attributes.runtimeVisibleAnnotations()),
        model.findAttribute(Attributes.runtimeInvisibleAnnotations()),
        className, "<class>", "()V", TargetKind.CLASS, map);

    // Field-level annotations
    for (FieldModel fm : model.fields()) {
      extractAnnotations(
          fm.findAttribute(Attributes.runtimeVisibleAnnotations()),
          fm.findAttribute(Attributes.runtimeInvisibleAnnotations()),
          className, "<field:" + fm.fieldName().stringValue() + ">",
          fm.fieldType().stringValue(), TargetKind.FIELD, map);
    }

    // Method-level and parameter annotations
    for (MethodModel mm : model.methods()) {
      String methodName = mm.methodName().stringValue();
      String methodDesc = mm.methodType().stringValue();
      var visible = mm.findAttribute(Attributes.runtimeVisibleAnnotations());
      var invisible = mm.findAttribute(Attributes.runtimeInvisibleAnnotations());
      visible.ifPresent(attr -> processAnnotations(attr.annotations(),
              className, methodName, methodDesc, TargetKind.METHOD, map));
      invisible.ifPresent(attr -> processAnnotations(attr.annotations(),
              className, methodName, methodDesc, TargetKind.METHOD, map));



      extractParameterAnnotations(
          mm.findAttribute(Attributes.runtimeVisibleParameterAnnotations()),
          mm.findAttribute(Attributes.runtimeInvisibleParameterAnnotations()),
          className, methodName, methodDesc, map);
    }

    return map;
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  private void extractAnnotations(
      java.util.Optional<RuntimeVisibleAnnotationsAttribute> visible,
      java.util.Optional<RuntimeInvisibleAnnotationsAttribute> invisible,
      String className, String methodName, String methodDescriptor,
      TargetKind targetKind, Multimap<Object, ConstantUsage> map) {
    visible.ifPresent(attr -> processAnnotations(attr.annotations(),
        className, methodName, methodDescriptor, targetKind, map));
    invisible.ifPresent(attr -> processAnnotations(attr.annotations(),
        className, methodName, methodDescriptor, targetKind, map));
  }

  private void extractParameterAnnotations(
      java.util.Optional<RuntimeVisibleParameterAnnotationsAttribute> visible,
      java.util.Optional<RuntimeInvisibleParameterAnnotationsAttribute> invisible,
      String className, String methodName, String methodDescriptor,
      Multimap<Object, ConstantUsage> map) {
    if (visible.isPresent()) {
      for (List<Annotation> paramAnns : visible.get().parameterAnnotations()) {
        processAnnotations(paramAnns, className, methodName, methodDescriptor,
            TargetKind.PARAMETER, map);
      }
    }
    if (invisible.isPresent()) {
      for (List<Annotation> paramAnns : invisible.get().parameterAnnotations()) {
        processAnnotations(paramAnns, className, methodName, methodDescriptor,
            TargetKind.PARAMETER, map);
      }
    }
  }

  private void processAnnotations(
      List<Annotation> annotations,
      String className, String methodName, String methodDescriptor,
      TargetKind targetKind, Multimap<Object, ConstantUsage> map) {
    for (Annotation ann : annotations) {
      String annDesc = ann.classSymbol().descriptorString();
      for (AnnotationElement elem : ann.elements()) {
        processAnnotationValue(
            elem.value(), annDesc, elem.name().stringValue(),
            className, methodName, methodDescriptor, targetKind, map);
      }
    }
  }

  private void processAnnotationValue(
      AnnotationValue value,
      String annotationDescriptor, String elementName,
      String className, String methodName, String methodDescriptor,
      TargetKind targetKind, Multimap<Object, ConstantUsage> map) {
    switch (value) {
      case AnnotationValue.OfString s ->
          record(s.stringValue(), annotationDescriptor, elementName,
              className, methodName, methodDescriptor, targetKind, map);
      case AnnotationValue.OfInt i ->
          record(i.intValue(), annotationDescriptor, elementName,
              className, methodName, methodDescriptor, targetKind, map);
      case AnnotationValue.OfLong l ->
          record(l.longValue(), annotationDescriptor, elementName,
              className, methodName, methodDescriptor, targetKind, map);
      case AnnotationValue.OfFloat f ->
          record(f.floatValue(), annotationDescriptor, elementName,
              className, methodName, methodDescriptor, targetKind, map);
      case AnnotationValue.OfDouble d ->
          record(d.doubleValue(), annotationDescriptor, elementName,
              className, methodName, methodDescriptor, targetKind, map);
      case AnnotationValue.OfBoolean b ->
          record(b.booleanValue(), annotationDescriptor, elementName,
              className, methodName, methodDescriptor, targetKind, map);
      case AnnotationValue.OfByte b ->
          record(b.byteValue(), annotationDescriptor, elementName,
              className, methodName, methodDescriptor, targetKind, map);
      case AnnotationValue.OfShort s ->
          record(s.shortValue(), annotationDescriptor, elementName,
              className, methodName, methodDescriptor, targetKind, map);
      case AnnotationValue.OfChar c ->
          record(c.charValue(), annotationDescriptor, elementName,
              className, methodName, methodDescriptor, targetKind, map);
      case AnnotationValue.OfArray arr -> {
        for (AnnotationValue element : arr.values()) {
          processAnnotationValue(element, annotationDescriptor, elementName,
              className, methodName, methodDescriptor, targetKind, map);
        }
      }
      case AnnotationValue.OfAnnotation nested ->
          processAnnotations(List.of(nested.annotation()),
              className, methodName, methodDescriptor, targetKind, map);
      // OfEnum and OfClass don't carry user-defined constant strings/numbers
      default -> {}
    }
  }

  private void record(
      Object constantValue,
      String annotationDescriptor, String elementName,
      String className, String methodName, String methodDescriptor,
      TargetKind targetKind, Multimap<Object, ConstantUsage> map) {
    UsageLocation location = new UsageLocation(className, methodName, methodDescriptor, 0, null);
    AnnotationValueContext ctx = new AnnotationValueContext(annotationDescriptor, elementName, targetKind);
    Collection<ConstantUsageInterpreter> interpreters =
        registry.getInterpreters(UsageType.ANNOTATION_VALUE);
    for (ConstantUsageInterpreter interp : interpreters) {
      map.put(constantValue, interp.interpret(location, ctx));
    }
  }
}
