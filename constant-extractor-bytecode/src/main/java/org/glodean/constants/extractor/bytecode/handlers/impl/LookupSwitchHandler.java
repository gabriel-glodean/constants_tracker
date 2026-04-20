package org.glodean.constants.extractor.bytecode.handlers.impl;

import java.lang.classfile.instruction.LookupSwitchInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for lookup switch instructions. Conservatively consumes the switch key from the stack.
 * Control flow to case targets is handled by the successor builder.
 */
final class LookupSwitchHandler implements InstructionHandler<LookupSwitchInstruction> {
  @Override
  public void handle(LookupSwitchInstruction lsi, State state, String tag) {
    state.stack.removeLast();
  }
}
