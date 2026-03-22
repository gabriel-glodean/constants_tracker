package org.glodean.constants.extractor.bytecode.types;

/**
 * Composite key that uniquely identifies a static field access in the abstract heap.
 *
 * <p>Used as the map key in {@link State#statics} to track which points-to set is
 * associated with each static field observed during analysis.
 *
 * @param owner the internal name of the declaring class (e.g., {@code "java/lang/System"})
 * @param name  the field name (e.g., {@code "out"})
 * @param desc  the JVM field descriptor (e.g., {@code "Ljava/io/PrintStream;"})
 */
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
