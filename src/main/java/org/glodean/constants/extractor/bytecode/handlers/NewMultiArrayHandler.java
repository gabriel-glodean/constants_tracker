package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.instruction.NewMultiArrayInstruction;
import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for multi-dimensional array creation instructions. Pops dimension counts and pushes a
 * newly created array reference.
 */
final class NewMultiArrayHandler implements InstructionHandler<NewMultiArrayInstruction> {
  @Override
  public void handle(NewMultiArrayInstruction nmaInstruction, State state, String tag) {
    for (int k = 0; k < nmaInstruction.dimensions(); k++) {
      state.stack.removeLast();
    }
    state.stack.addLast(
        PointsToSet.of(new ObjectReference(nmaInstruction.arrayType().asSymbol(), tag)));
  }
}
