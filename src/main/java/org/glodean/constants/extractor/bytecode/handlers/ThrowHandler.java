package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.instruction.ThrowInstruction;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for throw instructions. Conservatively models that a throwable object is consumed and
 * does not push any value. The control-flow handling for throws is performed by the successor
 * builder/exception handler wiring.
 *
 * <p>handler entry points.
 */
final class ThrowHandler implements InstructionHandler<ThrowInstruction> {
  @Override
  public void handle(ThrowInstruction ti, State state, String tag) {
    // consume throwable reference
    state.stack.removeLast();
    // entry)
  }
}
