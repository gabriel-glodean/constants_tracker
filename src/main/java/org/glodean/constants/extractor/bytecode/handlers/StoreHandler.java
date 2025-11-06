package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

import java.lang.classfile.instruction.StoreInstruction;

final class StoreHandler implements InstructionHandler<StoreInstruction> {
    @Override
    public void handle(StoreInstruction si, State state, String tag) {
        int var = si.slot();
        PointsToSet v = state.stack.removeLast();
        state.locals.set(var, v == null ? null : v.copy());
    }
}
