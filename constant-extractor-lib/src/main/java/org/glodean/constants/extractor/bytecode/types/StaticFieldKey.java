package org.glodean.constants.extractor.bytecode.types;

public record StaticFieldKey(String owner, String name, String desc) {

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof StaticFieldKey(String owner1, String name1, String desc1))) return false;
    return owner.equals(owner1) && name.equals(name1) && desc.equals(desc1);
  }

  @Override
  public String toString() {
    return owner + "::" + name + " " + desc;
  }
}
