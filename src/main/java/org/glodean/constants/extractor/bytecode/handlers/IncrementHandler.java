package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.instruction.IncrementInstruction;
import org.glodean.constants.extractor.bytecode.types.ConstantPropagatingEntity;
import org.glodean.constants.extractor.bytecode.types.NumericConstant;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handles IINC (increment) bytecode instruction by updating the local variable's points-to set
 * using constant propagation semantics.
 */
final class IncrementHandler implements InstructionHandler<IncrementInstruction> {
  @Override
  public void handle(IncrementInstruction ii, State state, String tag) {
    int var = ii.slot();
    PointsToSet local = state.locals.get(var);
    PointsToSet newSet = new PointsToSet();
    NumericConstant constant = new NumericConstant(ii.constant());
    for (var value : local) {
      newSet.add(((ConstantPropagatingEntity) value).propagate(constant));
    }
    state.locals.set(var, newSet);
  }
}
