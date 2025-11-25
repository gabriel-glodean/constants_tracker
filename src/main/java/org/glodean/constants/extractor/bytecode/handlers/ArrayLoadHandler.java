package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.instruction.ArrayLoadInstruction;
import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for array load bytecode instructions (e.g., IALOAD, AALOAD).
 *
 * <p>Pops the index and array reference from the stack, looks up the possible element set for the
 * array and pushes a conservative result onto the stack. If elements aren't tracked, it creates a
 * placeholder element based on the component type.
 */
final class ArrayLoadHandler implements InstructionHandler<ArrayLoadInstruction> {
  /**
   * Process an array-load instruction updating the abstract state accordingly.
   *
   * @param instruction the array load instruction
   * @param state the abstract bytecode state to update
   * @param tag diagnostic tag (method@pc) used for created synthetic references
   */
  @Override
  public void handle(ArrayLoadInstruction instruction, State state, String tag) {
    state.stack.removeLast();
    PointsToSet arr = state.stack.removeLast();
    PointsToSet res = new PointsToSet();
    if (arr != null)
      for (StackAndParameterEntity o : arr) {
        var els = state.arrayElements.get(o);
        if (els != null) {
          res.addAll(els);
        } else {
          res.add(
              StackAndParameterEntity.convert(
                  ((ObjectReference) o).descriptor().componentType(), tag));
        }
      }
    state.stack.addLast(res);
  }
}
