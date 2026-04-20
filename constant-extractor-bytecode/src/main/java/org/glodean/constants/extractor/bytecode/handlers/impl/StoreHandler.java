package org.glodean.constants.extractor.bytecode.handlers.impl;

import java.lang.classfile.instruction.StoreInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for local-variable store instructions (e.g., {@code istore}, {@code astore}).
 *
 * <p>Pops the top points-to set from the operand stack and stores a copy into the
 * addressed local variable slot.
 */
final class StoreHandler implements InstructionHandler<StoreInstruction> {

  /**
   * {@inheritDoc}
   *
   * <p>Pops the top operand and writes a defensive copy into {@code locals[slot]}.
   * A {@code null} pop (empty or uninitialised slot) is written as {@code null}.
   */
  @Override
  public void handle(StoreInstruction si, State state, String tag) {
    int var = si.slot();
    PointsToSet v = state.stack.removeLast();
    state.locals.set(var, v == null ? null : v.copy());
  }
}
