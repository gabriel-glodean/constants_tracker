package org.glodean.constants.extractor.bytecode.types;

import java.util.*;

/**
 * Represents the per-instruction abstract program state used by the bytecode analyzer.
 *
 * <p>Contains locals, operand stack, heap-like maps for fields/statics/array elements and
 * operations to copy and union states.
 */
public final class State {
  public final List<PointsToSet> locals;
  public final List<PointsToSet> stack;
  public final Map<FieldKey, PointsToSet> heap = new HashMap<>();
  public final Map<StaticFieldKey, PointsToSet> statics = new HashMap<>();
  public final Map<StackAndParameterEntity, PointsToSet> arrayElements = new HashMap<>();

  public State(int maxLocals) {
    this.locals = new ArrayList<>(Collections.nCopies(maxLocals, null));
    this.stack = new ArrayList<>();
  }

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

  /** Component-wise union; returns true iff any set strictly increased. */
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
      } else if (a.addAll(b)) {
        changed = true;
      }
    }

    for (int i = min; i < src.stack.size(); i++) {
      PointsToSet set = src.stack.get(i);
      if (set == null) continue;
      stack.add(set);
      changed = true;
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
