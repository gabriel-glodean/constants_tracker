package org.glodean.constants.extractor.bytecode.types;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.classfile.TypeKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for NumericConstant behaviors. */
class NumericConstantTest {

  private static boolean containsNumeric(Set<Number> set, long val) {
    return set.stream().anyMatch(n -> n.longValue() == val);
  }

  @Test
  void size_detectsSingleAndDoubleCell() {
    NumericConstant single = new NumericConstant(42);
    NumericConstant longVal = new NumericConstant(42L);
    NumericConstant doubleVal = new NumericConstant(3.14d);

    assertEquals(SizeType.SINGLE_CELL, single.size());
    assertEquals(SizeType.DOUBLE_CELL, longVal.size());
    assertEquals(SizeType.DOUBLE_CELL, doubleVal.size());
  }

  @Test
  void propagate_withAnotherNumeric_createsConstantPropagation() {
    NumericConstant a = new NumericConstant(1);
    NumericConstant b = new NumericConstant(2L);

    var result = a.propagate(b);
    assertTrue(
        result instanceof ConstantPropagation,
        "propagation of two numerics should yield ConstantPropagation");

    ConstantPropagation cp = (ConstantPropagation) result;
    assertEquals(2, cp.values().size());
    assertTrue(containsNumeric(cp.values(), 1));
    assertTrue(containsNumeric(cp.values(), 2));
  }

  @Test
  void propagate_withConstantPropagation_mergesValuesAndSize() {
    ConstantPropagation base = new ConstantPropagation(Set.of(5));
    NumericConstant add = new NumericConstant(6L);

    var res = base.propagate(add);
    assertTrue(res instanceof ConstantPropagation);

    ConstantPropagation merged = (ConstantPropagation) res;
    assertEquals(2, merged.values().size());
    assertTrue(containsNumeric(merged.values(), 5));
    assertTrue(containsNumeric(merged.values(), 6));
    // size should be at least as big as the larger of the two
    assertTrue(merged.size().ordinal() >= base.size().ordinal());
  }

  @Test
  void convertTo_performsNumericNarrowingAndWidening() {
    NumericConstant n = new NumericConstant(130); // fits into int but not byte as positive >127
    var converted = n.convertTo(TypeKind.BYTE, "site");
    assertTrue(converted instanceof NumericConstant);
    Number v = ((NumericConstant) converted).value();
    assertEquals((byte) 130, v.byteValue());
  }
}
