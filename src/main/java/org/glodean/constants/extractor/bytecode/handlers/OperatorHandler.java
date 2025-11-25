package org.glodean.constants.extractor.bytecode.handlers;

import static java.lang.constant.ConstantDescs.CD_int;

import com.google.common.collect.Streams;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.OperatorInstruction;
import org.glodean.constants.extractor.bytecode.types.ConstantPropagatingEntity;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.PrimitiveValue;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handles operator / arithmetic instructions. Performs constant propagation for binary operations
 * when possible and models arraylength.
 */
final class OperatorHandler implements InstructionHandler<OperatorInstruction> {
  @Override
  public void handle(OperatorInstruction oi, State state, String tag) {
    if (oi.opcode() == Opcode.ARRAYLENGTH) {
      state.stack.removeLast();
      state.stack.addLast(PointsToSet.of(new PrimitiveValue(CD_int, tag)));
    } else {
      var pointsToSet = new PointsToSet();
      Streams.forEachPair(
          state.stack.removeLast().stream(),
          state.stack.removeLast().stream(),
          (first, second) ->
              pointsToSet.add(
                  ((ConstantPropagatingEntity) first)
                      .propagate((ConstantPropagatingEntity) second)));
      state.stack.addLast(pointsToSet);
    }
  }
}
