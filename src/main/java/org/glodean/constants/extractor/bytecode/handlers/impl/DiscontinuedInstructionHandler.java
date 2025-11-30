package org.glodean.constants.extractor.bytecode.handlers.impl;

import java.lang.classfile.instruction.DiscontinuedInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.State;

final class DiscontinuedInstructionHandler implements InstructionHandler<DiscontinuedInstruction> {
  @Override
  public void handle(DiscontinuedInstruction instruction, State state, String tag) {
    throw new UnsupportedOperationException(
        "JSR and RET instructions are discontinued and ignored.");
  }
}
