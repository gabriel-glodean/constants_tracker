package org.glodean.constants.extractor.bytecode.handlers.impl;

import static java.lang.classfile.Opcode.*;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.util.EnumSet;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for conditional and unconditional branch instructions.
 *
 * <p>It simulates the stack effects of branch instructions: most conditional branches pop one
 * operand, integer comparison branches pop two operands. GOTO instructions are treated as having no
 * stack pops because control flow transfers unconditionally.
 */
final class BranchHandler implements InstructionHandler<BranchInstruction> {
  private final EnumSet<Opcode> DOUBLE_POPPING_BRANCHES =
      EnumSet.of(
          IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE);

  @Override
  public void handle(BranchInstruction bi, State state, String tag) {
    var opcode = bi.opcode();
    if (opcode == Opcode.GOTO || opcode == Opcode.GOTO_W) {
      return;
    }
    state.stack.removeLast();
    if (DOUBLE_POPPING_BRANCHES.contains(opcode)) {
      state.stack.removeLast();
    }
  }
}
