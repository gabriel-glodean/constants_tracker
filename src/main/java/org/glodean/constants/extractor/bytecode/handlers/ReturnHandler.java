package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ReturnInstruction;
import org.glodean.constants.extractor.bytecode.types.State;

final class ReturnHandler implements InstructionHandler<ReturnInstruction> {
  @Override
  public void handle(ReturnInstruction ri, State state, String tag) {
    if (ri.typeKind() != TypeKind.VOID) {
      state.stack.removeLast();
    }
  }
}
