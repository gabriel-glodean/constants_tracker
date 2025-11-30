package org.glodean.constants.extractor.bytecode.types;

import static java.lang.constant.ConstantDescs.*;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.util.Objects;

/** Representation of a primitive (non-reference) value in the abstract state. */
public record PrimitiveValue(ClassDesc descriptor, String site)
    implements ConstantPropagatingEntity, ConvertibleEntity {

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    PrimitiveValue that = (PrimitiveValue) o;
    return Objects.equals(descriptor, that.descriptor) && Objects.equals(site, that.site);
  }

  @Override
  public SizeType size() {
    return descriptor == CD_long || descriptor == CD_double
        ? SizeType.DOUBLE_CELL
        : SizeType.SINGLE_CELL;
  }

  @Override
  public int hashCode() {
    return Objects.hash(descriptor, site);
  }

  @Override
  public String toString() {
    return descriptor.descriptorString() + "@" + site;
  }

  @Override
  public ConstantPropagatingEntity propagate(ConstantPropagatingEntity constant) {
    return constant;
  }

  @Override
  public ConvertibleEntity convertTo(TypeKind targetType, String site) {
    return new PrimitiveValue(targetType.upperBound(), site);
  }
}
