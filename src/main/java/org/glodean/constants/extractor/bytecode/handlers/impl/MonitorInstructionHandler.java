package org.glodean.constants.extractor.bytecode.handlers.impl;

import java.lang.classfile.instruction.MonitorInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for monitor enter/exit instructions (monitorenter/monitorexit).
 *
 * <p>Conservatively consumes the object reference from the stack. ENTER and EXIT semantics and for
 * reentrant monitors.
 */
final class MonitorInstructionHandler implements InstructionHandler<MonitorInstruction> {
  @Override
  public void handle(MonitorInstruction mi, State state, String tag) {
    // monitor instructions consume an object reference
    state.stack.removeLast();
  }
}
