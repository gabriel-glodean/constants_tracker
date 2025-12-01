package org.glodean.constants.extractor.bytecode.types;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.classfile.TypeKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for ConstantPropagation behaviors. */
class ConstantPropagationTest {

  private static boolean containsNumeric(Set<Number> set, long val) {
    return set.stream().anyMatch(n -> n.longValue() == val);
  }

  @Test
  void equalsAndHashCode_workAsExpected() {
    ConstantPropagation a = new ConstantPropagation(Set.of(1, 2));
    ConstantPropagation b = new ConstantPropagation(Set.of(1, 2));
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertEquals(a.toString(), b.toString());
  }

  @Test
  void toSize_returnsNewInstanceWhenDifferent() {
    ConstantPropagation a = new ConstantPropagation(Set.of(1));
    ConstantPropagation same = a.toSize(a.size());
    assertSame(a, same, "toSize with same size should return same instance");

    ConstantPropagation widened = a.toSize(SizeType.DOUBLE_CELL);
    assertNotSame(a, widened);
    assertEquals(SizeType.DOUBLE_CELL, widened.size());
  }

  @Test
  void propagate_mergesWithOtherPropagation() {
    ConstantPropagation p1 = new ConstantPropagation(Set.of(1, 2));
    ConstantPropagation p2 = new ConstantPropagation(Set.of(3));
    var merged = p1.propagate(p2);
    assertTrue(merged instanceof ConstantPropagation);
    ConstantPropagation cp = (ConstantPropagation) merged;
    assertEquals(3, cp.values().size());
    assertTrue(containsNumeric(cp.values(), 1));
    assertTrue(containsNumeric(cp.values(), 2));
    assertTrue(containsNumeric(cp.values(), 3));
  }

  @Test
  void convertTo_returnsPropagationWithTargetSize() {
    ConstantPropagation p = new ConstantPropagation(Set.of(7));
    var converted = p.convertTo(TypeKind.LONG, "site");
    assertTrue(converted instanceof ConstantPropagation);
    ConstantPropagation cp = (ConstantPropagation) converted;
    assertEquals(SizeType.fromType(TypeKind.LONG), cp.size());
    assertTrue(containsNumeric(cp.values(), 7));
  }
}
