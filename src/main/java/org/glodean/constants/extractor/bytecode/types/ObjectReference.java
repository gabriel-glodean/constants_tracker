package org.glodean.constants.extractor.bytecode.types;

import java.lang.constant.ClassDesc;
import java.util.Objects;

public record ObjectReference(ClassDesc descriptor, String site)
    implements StackAndParameterEntity {
  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ObjectReference that = (ObjectReference) o;
    return Objects.equals(descriptor, that.descriptor) && Objects.equals(site, that.site);
  }

  @Override
  public int hashCode() {
    return Objects.hash(descriptor, site);
  }

  @Override
  public String toString() {
    return descriptor.descriptorString() + "@" + site;
  }

  public boolean isArrayReference() {
    return descriptor.isArray();
  }
}
