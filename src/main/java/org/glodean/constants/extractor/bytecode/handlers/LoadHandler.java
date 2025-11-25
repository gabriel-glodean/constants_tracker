package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.instruction.LoadInstruction;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for load instructions (e.g., ILOAD, ALOAD). Copies the local variable slot onto the
 * operand stack.
 */
final class LoadHandler implements InstructionHandler<LoadInstruction> {
  @Override
  public void handle(LoadInstruction li, State state, String tag) {
    var variable = state.locals.get(li.slot());
    state.stack.addLast(variable == null ? null : variable.copy());
  }
}
