package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.instruction.LoadInstruction;
import org.glodean.constants.extractor.bytecode.types.State;

final class LoadHandler implements InstructionHandler<LoadInstruction> {
  @Override
  public void handle(LoadInstruction li, State state, String tag) {
    var variable = state.locals.get(li.slot());
    state.stack.addLast(variable == null ? null : variable.copy());
  }
}
