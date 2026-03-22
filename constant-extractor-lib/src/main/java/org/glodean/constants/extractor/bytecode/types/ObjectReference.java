package org.glodean.constants.extractor.bytecode.types;

import java.lang.constant.ClassDesc;
import java.util.Objects;

/**
 * Allocation-site style representation of an object reference (descriptor + creation site tag).
 *
 * <p>In points-to analysis, runtime-allocated objects are abstracted by their allocation site
 * (where in the code they were created). This class represents such abstract objects, combining:
 * <ul>
 *   <li><b>descriptor:</b> The object's type (e.g., {@code ClassDesc} for {@code java.lang.String})</li>
 *   <li><b>site:</b> A unique tag identifying the creation point (e.g., "MyClass::method#new@42")</li>
 * </ul>
 *
 * <p><b>Example:</b> Two {@code new StringBuilder()} calls at different bytecode offsets create
 * two distinct {@code ObjectReference}s, even though they have the same type. This allows the
 * analysis to distinguish between different object instances.
 *
 * <p>Object references differ from {@link Constant}s: references represent runtime-allocated
 * objects with unknown identity, while constants have known compile-time values.
 *
 * @param descriptor the object's runtime type
 * @param site allocation site tag (e.g., "ClassName::methodName#local2")
 */
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

  /**
   * Returns {@code true} if this reference points to an array type.
   *
   * @return {@code true} if {@link #descriptor()} is an array descriptor
   */
  public boolean isArrayReference() {
    return descriptor.isArray();
  }
}
