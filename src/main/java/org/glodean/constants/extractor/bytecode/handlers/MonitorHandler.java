package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.instruction.MonitorInstruction;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for monitor enter/exit instructions (monitorenter/monitorexit).
 *
 * <p>Conservatively consumes the object reference from the stack. TODO: verify correctness for both
 * ENTER and EXIT semantics and for reentrant monitors.
 */
final class MonitorHandler implements InstructionHandler<MonitorInstruction> {
  @Override
  public void handle(MonitorInstruction mi, State state, String tag) {
    // monitor instructions consume an object reference
    state.stack.removeLast();
    // TODO: verify correctness (monitors don't push any value; consider reentrancy semantics)
  }
}
