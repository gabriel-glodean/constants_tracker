package org.glodean.constants.extractor.bytecode.handlers.impl;

import java.lang.classfile.instruction.ArrayStoreInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for array store bytecode instructions (e.g., IASTORE, AASTORE).
 *
 * <p>Pops the value, index and array reference and updates the tracked element set for the
 * referenced array(s) conservatively.
 */
final class ArrayStoreHandler implements InstructionHandler<ArrayStoreInstruction> {
  /**
   * Update abstract state for an array store instruction.
   *
   * @param instruction the array store instruction
   * @param state the abstract bytecode state to update
   * @param tag diagnostic tag (method@pc)
   */
  @Override
  public void handle(ArrayStoreInstruction instruction, State state, String tag) {
    PointsToSet v = state.stack.removeLast();
    state.stack.removeLast();
    PointsToSet arr = state.stack.removeLast();
    if (arr != null)
      for (StackAndParameterEntity o : arr) {
        state.arrayElements.computeIfAbsent(o, _ -> new PointsToSet()).addAllFrom(v);
      }
  }
}
