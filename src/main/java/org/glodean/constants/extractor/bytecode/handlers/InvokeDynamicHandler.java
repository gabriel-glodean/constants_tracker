package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.instruction.InvokeDynamicInstruction;
import org.glodean.constants.extractor.bytecode.types.State;

/** Handles dynamic invocation bytecode instructions (e.g., {@code invokedynamic}). */
final class InvokeDynamicHandler implements InstructionHandler<InvokeDynamicInstruction> {

  /**
   * Handles an {@link InvokeDynamicInstruction} by updating the state stack according to its
   * signature.
   *
   * @param idi the invokedynamic instruction
   * @param state the current bytecode state
   * @param tag an optional tag for context
   */
  @Override
  public void handle(InvokeDynamicInstruction idi, State state, String tag) {
    int argCount = idi.typeSymbol().parameterCount();
    for (int k = 0; k < argCount; k++) state.stack.removeLast();
    var returnType = idi.typeSymbol().returnType();
    if (returnType != java.lang.constant.ConstantDescs.CD_void) {
      if (returnType.isPrimitive()) {
        state.stack.addLast(
            org.glodean.constants.extractor.bytecode.types.PointsToSet.of(
                new org.glodean.constants.extractor.bytecode.types.PrimitiveValue(
                    returnType, tag)));
      } else {
        state.stack.addLast(
            org.glodean.constants.extractor.bytecode.types.PointsToSet.of(
                new org.glodean.constants.extractor.bytecode.types.ObjectReference(
                    returnType, tag)));
      }
    }
  }
}
