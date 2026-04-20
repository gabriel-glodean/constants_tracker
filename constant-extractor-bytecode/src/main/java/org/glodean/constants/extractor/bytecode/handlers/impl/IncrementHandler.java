package org.glodean.constants.extractor.bytecode.handlers.impl;

import static java.lang.constant.ConstantDescs.CD_int;

import java.lang.classfile.instruction.IncrementInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.ConstantPropagatingEntity;
import org.glodean.constants.extractor.bytecode.types.NumericConstant;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.PrimitiveValue;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handles IINC (increment) bytecode instruction by updating the local variable's points-to set
 * using constant propagation semantics.
 */
final class IncrementHandler implements InstructionHandler<IncrementInstruction> {

  /**
   * {@inheritDoc}
   *
   * <p>Propagates the increment constant through every {@link ConstantPropagatingEntity}
   * in the local variable's points-to set. If no propagated values result (e.g., the local
   * is an opaque reference), the local is replaced with a fresh {@link PrimitiveValue}.
   */
  @Override
  public void handle(IncrementInstruction ii, State state, String tag) {
    int var = ii.slot();
    PointsToSet local = state.locals.get(var);
    PointsToSet newSet = new PointsToSet();
    NumericConstant constant = new NumericConstant(ii.constant());
    for (var value : local) {
      if (value instanceof ConstantPropagatingEntity cpe) {
        newSet.add(cpe.propagate(constant));
      }
    }
    if (newSet.isEmpty()) {
      newSet.add(new PrimitiveValue(CD_int, tag));
    }
    state.locals.set(var, newSet);
  }
}
