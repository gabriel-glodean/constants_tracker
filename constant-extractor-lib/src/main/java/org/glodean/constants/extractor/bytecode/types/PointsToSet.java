package org.glodean.constants.extractor.bytecode.types;

import java.util.Collection;
import java.util.HashSet;

/**
 * Convenience set type representing possible abstract objects/values a variable or stack slot can
 * point to.
 *
 * <p>A widening limit ({@link #MAX_SIZE}) caps the maximum number of entries. Once the cap is
 * reached, further additions are silently dropped. This guarantees finite lattice height and
 * therefore worklist convergence, at the cost of precision for extremely polymorphic slots.
 */
public final class PointsToSet extends HashSet<StackAndParameterEntity> {

  /**
   * Maximum number of entities tracked per slot. Beyond this, additions are ignored (widening to
   * Top). The value is high enough to cover realistic cases but prevents explosion in methods with
   * many branches (e.g. large switch cascades producing hundreds of ConstantPropagation variants).
   */
  static final int MAX_SIZE = 32;

  public PointsToSet() {}

  public PointsToSet(Collection<StackAndParameterEntity> c) {
    super(c);
  }

  @Override
  public boolean add(StackAndParameterEntity e) {
    if (size() >= MAX_SIZE) return false;
    return super.add(e);
  }

  @Override
  public boolean addAll(Collection<? extends StackAndParameterEntity> c) {
    if (size() >= MAX_SIZE) return false;
    boolean changed = false;
    for (StackAndParameterEntity e : c) {
      if (size() >= MAX_SIZE) break;
      changed |= super.add(e);
    }
    return changed;
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
