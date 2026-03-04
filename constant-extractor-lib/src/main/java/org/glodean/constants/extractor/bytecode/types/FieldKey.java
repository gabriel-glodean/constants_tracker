package org.glodean.constants.extractor.bytecode.types;

import java.util.Objects;

/** Key representing an instance field access: receiver + owner + name + descriptor. */
public record FieldKey(StackAndParameterEntity receiver, String owner, String name, String desc) {

  @Override
  public boolean equals(Object o) {
    if (!(o
        instanceof
        FieldKey(StackAndParameterEntity receiver1, String owner1, String name1, String desc1)))
      return false;
    return Objects.equals(receiver, receiver1)
        && owner.equals(owner1)
        && name.equals(name1)
        && desc.equals(desc1);
  }

  @Override
  public String toString() {
    return receiver + "." + owner + "::" + name + " " + desc;
  }
}
