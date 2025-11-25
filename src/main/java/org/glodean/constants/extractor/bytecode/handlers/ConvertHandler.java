package org.glodean.constants.extractor.bytecode.handlers;

import static java.lang.constant.ConstantDescs.CD_int;

import java.lang.classfile.instruction.ConvertInstruction;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for primitive conversion instructions (e.g., I2L, F2D). Conservatively models the
 * conversion by popping the source primitive and pushing a placeholder primitive value for the
 * destination type.
 *
 * <p>TODO: verify propagation of constant values through conversions (precision loss, widening
 * rules) and implement precise numeric conversions if required.
 */
final class ConvertHandler implements InstructionHandler<ConvertInstruction> {
  @Override
  public void handle(ConvertInstruction ci, State state, String tag) {
    // remove source
    state.stack.removeLast();
    // push a conservative primitive placeholder for the result (default to int)
    state.stack.addLast(
        org.glodean.constants.extractor.bytecode.types.PointsToSet.of(
            new org.glodean.constants.extractor.bytecode.types.PrimitiveValue(CD_int, tag)));
    // TODO: verify correctness around precision and propagate actual numeric constants when
    // possible
  }
}
