package org.glodean.constants.extractor.bytecode.handlers.impl;

import java.lang.classfile.instruction.DiscontinuedInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for discontinued JVM instructions ({@code jsr} and {@code ret}).
 *
 * <p>These instructions were removed in Java 7 (class-file version 51) and should never
 * appear in modern bytecode. The handler throws {@link UnsupportedOperationException} to
 * make any accidental encounter immediately visible.
 */
final class DiscontinuedInstructionHandler implements InstructionHandler<DiscontinuedInstruction> {

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always — JSR/RET are discontinued
   */
  @Override
  public void handle(DiscontinuedInstruction instruction, State state, String tag) {
    throw new UnsupportedOperationException(
        "JSR and RET instructions are discontinued and ignored.");
  }
}
