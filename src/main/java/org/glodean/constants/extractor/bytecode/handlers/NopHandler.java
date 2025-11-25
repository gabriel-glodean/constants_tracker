package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.instruction.NopInstruction;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for NOP instructions; performs no state changes. differences exist across JDK versions
 * that matter for analysis.
 */
final class NopHandler implements InstructionHandler<NopInstruction> {
  @Override
  public void handle(NopInstruction ni, State state, String tag) {
    // deliberately no-op
    // for analysis
  }
}
