package org.glodean.constants.extractor.bytecode.types;

import java.util.Collection;
import java.util.HashSet;

/**
 * Convenience set type representing possible abstract objects/values a variable or stack slot can
 * point to.
 *
 * <p>In the points-to analysis, each local variable or stack position holds a {@code PointsToSet}
 * containing all possible runtime values at that program point. For example:
 * <ul>
 *   <li>A constant-initialized variable: {@code {Constant("hello")}}</li>
 *   <li>A phi-merged variable: {@code {Constant("hello"), Constant("world")}}</li>
 *   <li>An object reference: {@code {ObjectReference(String, "local#1")}}</li>
 * </ul>
 *
 * <p><b>Widening:</b> A size limit ({@link #MAX_SIZE}) caps the maximum number of entries.
 * Once the cap is reached, further additions are silently dropped (approximating "Top" in
 * lattice terms). This guarantees termination of the dataflow analysis—without it, large
 * switch statements or loops could create unbounded sets, preventing convergence.
 *
 * <p>The widening threshold is tuned for typical Java methods: high enough to be precise
 * (32 distinct values), but low enough to ensure fast convergence even in pathological cases.
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
