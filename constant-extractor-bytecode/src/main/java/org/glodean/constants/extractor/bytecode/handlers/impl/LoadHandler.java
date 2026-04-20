package org.glodean.constants.extractor.bytecode.handlers.impl;

import java.lang.classfile.instruction.LoadInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for load instructions (e.g., ILOAD, ALOAD). Copies the local variable slot onto the
 * operand stack.
 */
final class LoadHandler implements InstructionHandler<LoadInstruction> {

  /**
   * {@inheritDoc}
   *
   * <p>Reads the points-to set from the local variable at {@code slot} and pushes a copy
   * onto the operand stack. Pushes {@code null} if the slot is uninitialised.
   */
  @Override
  public void handle(LoadInstruction li, State state, String tag) {
    var variable = state.locals.get(li.slot());
    state.stack.addLast(variable == null ? null : variable.copy());
  }
}
