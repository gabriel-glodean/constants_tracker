package org.glodean.constants.extractor.bytecode.handlers.impl;

import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for creation of reference arrays (e.g., anewarray). Pops the length and pushes the
 * newly-created array reference.
 */
final class NewReferenceArrayHandler implements InstructionHandler<NewReferenceArrayInstruction> {
  @Override
  public void handle(NewReferenceArrayInstruction nai, State state, String tag) {
    state.stack.removeLast();
    state.stack.addLast(PointsToSet.of(new ObjectReference(nai.componentType().asSymbol(), tag)));
  }
}
