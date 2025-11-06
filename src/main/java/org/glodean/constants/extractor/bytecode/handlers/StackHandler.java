package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.State;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.StackInstruction;

final class StackHandler implements InstructionHandler<StackInstruction>{
    @Override
    public void handle(StackInstruction si, State state, String tag) {
        if (si.opcode() == Opcode.DUP) {
            state.stack.addLast(state.stack.getLast());
        }
    }
}
