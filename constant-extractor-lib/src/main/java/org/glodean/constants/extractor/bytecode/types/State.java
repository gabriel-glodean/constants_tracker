package org.glodean.constants.extractor.bytecode.types;

import java.util.*;

/**
 * Represents the per-instruction abstract program state used by the bytecode analyzer.
 *
 * <p>This is the core data structure for the dataflow analysis. It models:
 * <ul>
 *   <li><b>locals</b>: Local variable slots (points-to sets for each variable)</li>
 *   <li><b>stack</b>: Operand stack (points-to sets for each stack position)</li>
 *   <li><b>heap</b>: Instance field values (object ref → field → points-to set)</li>
 *   <li><b>statics</b>: Static field values (class.field → points-to set)</li>
 *   <li><b>arrayElements</b>: Array element values (array ref → points-to set)</li>
 * </ul>
 *
 * <p>The analysis is flow-sensitive: each bytecode instruction has an IN state (before)
 * and OUT state (after). States are joined at control flow merge points using
 * {@link #unionWith(State)}.
 *
 * <p><b>Points-to sets</b> track possible runtime values. For constants, the set contains
 * a {@link Constant}; for objects, it contains {@link ObjectReference}s.
 */
public final class State {
  public final List<PointsToSet> stack;
  public final List<PointsToSet> locals;
  public final Map<FieldKey, PointsToSet> heap = new HashMap<>();
  public final Map<StaticFieldKey, PointsToSet> statics = new HashMap<>();
  public final Map<StackAndParameterEntity, PointsToSet> arrayElements = new HashMap<>();

  /**
   * Creates a new {@code State} with {@code maxLocals} local variable slots and an empty stack.
   *
   * @param maxLocals the number of local variable slots (from the method's {@code Code} attribute)
   */
  public State(int maxLocals) {
    this.locals = new ArrayList<>(Collections.nCopies(maxLocals, null));
    this.stack = new ArrayList<>();
  }

  /**
   * Returns a deep copy of this state; all points-to sets are cloned so that mutations
   * to the copy do not affect the original.
   *
   * @return an independent deep copy of this {@code State}
   */
  public State copy() {
    State s = new State(locals.size());
    for (int i = 0; i < locals.size(); i++)
      s.locals.set(i, locals.get(i) == null ? null : locals.get(i).copy());
    for (int i = 0; i < stack.size(); i++)
      s.stack.add(stack.get(i) == null ? null : stack.get(i).copy());
    heap.forEach((k, v) -> s.heap.put(k, v.copy()));
    statics.forEach((k, v) -> s.statics.put(k, v.copy()));
    arrayElements.forEach((k, v) -> s.arrayElements.put(k, v.copy()));
    return s;
  }

  /**
   * Performs a component-wise set-union of {@code src} into this state.
   *
   * <p>For each component (locals, stack, heap, statics, arrayElements) the points-to sets
   * of {@code src} are merged into the corresponding sets of {@code this}. The widening
   * threshold in {@link PointsToSet} guarantees that this operation always terminates.
   *
   * @param src the state to merge from; must have the same number of local-variable slots
   * @return {@code true} if any points-to set in this state strictly grew as a result
   */
  public boolean unionWith(State src) {
    boolean changed = false;
    // Locals
    for (int i = 0; i < locals.size(); i++) {
      PointsToSet a = locals.get(i), b = src.locals.get(i);
      if (b == null) continue;
      if (a == null) {
        locals.set(i, b.copy());
        changed = true;
      } else if (a.addAll(b)) {
        changed = true;
      }
    }

    int min = Math.min(stack.size(), src.stack.size());
    for (int i = 0; i < min; i++) {
      PointsToSet a = stack.get(i), b = src.stack.get(i);
      if (b == null) continue;
      if (a == null) {
        stack.set(i, b.copy());
        changed = true;
      } else {
        if (a.addAll(b)) {
          changed = true;
        }
      }
    }

    // Only extend the stack if it was previously empty (initial merge);
    // otherwise differing depths are caused by divergent control flow and
    // we conservatively keep the current depth.
    if (stack.isEmpty()) {
      for (int i = min; i < src.stack.size(); i++) {
        PointsToSet set = src.stack.get(i);
        if (set == null) continue;
        stack.add(set.copy());
        changed = true;
      }
    }
    // Heap-like maps
    changed |= unionMap(this.heap, src.heap);
    changed |= unionMap(this.statics, src.statics);
    changed |= unionMap(this.arrayElements, src.arrayElements);
    return changed;
  }

  private static <K> boolean unionMap(Map<K, PointsToSet> dst, Map<K, PointsToSet> src) {
    boolean ch = false;
    for (var e : src.entrySet()) {
      PointsToSet cur = dst.get(e.getKey());
      if (cur == null) {
        dst.put(e.getKey(), e.getValue().copy());
        ch = true;
      } else if (cur.addAll(e.getValue())) ch = true;
    }
    return ch;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    State state = (State) o;
    return Objects.equals(locals, state.locals)
        && Objects.equals(stack, state.stack)
        && Objects.equals(heap, state.heap)
        && Objects.equals(statics, state.statics)
        && Objects.equals(arrayElements, state.arrayElements);
  }

  @Override
  public int hashCode() {
    return Objects.hash(locals, stack, heap, statics, arrayElements);
  }
}
