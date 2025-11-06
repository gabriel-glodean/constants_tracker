package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

import java.lang.classfile.instruction.ArrayStoreInstruction;

final class ArrayStoreHandler implements InstructionHandler<ArrayStoreInstruction> {
    @Override
    public void handle(ArrayStoreInstruction instruction, State state, String tag) {
        PointsToSet v = state.stack.removeLast();
        state.stack.removeLast();
        PointsToSet arr = state.stack.removeLast();
        if (arr != null) for (StackAndParameterEntity o : arr) {
            state.arrayElements.computeIfAbsent(o, _ -> new PointsToSet()).addAllFrom(v);
        }
    }
}
