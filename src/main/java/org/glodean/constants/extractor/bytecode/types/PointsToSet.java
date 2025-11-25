package org.glodean.constants.extractor.bytecode.types;

import java.util.Collection;
import java.util.HashSet;

/**
 * Convenience set type representing possible abstract objects/values a variable or stack slot can
 * point to.
 */
public final class PointsToSet extends HashSet<StackAndParameterEntity> {
  public PointsToSet() {}

  public PointsToSet(Collection<StackAndParameterEntity> c) {
    super(c);
  }

  public static PointsToSet of(StackAndParameterEntity o) {
    var s = new PointsToSet();
    s.add(o);
    return s;
  }

  public PointsToSet copy() {
    return new PointsToSet(this);
  }

  public void addAllFrom(PointsToSet other) {
    if (other != null) this.addAll(other);
  }
}
