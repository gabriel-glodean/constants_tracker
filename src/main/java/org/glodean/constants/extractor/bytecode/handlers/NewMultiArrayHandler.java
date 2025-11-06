package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

import java.lang.classfile.instruction.NewMultiArrayInstruction;

final class NewMultiArrayHandler implements InstructionHandler<NewMultiArrayInstruction> {
    @Override
    public void handle(NewMultiArrayInstruction nmaInstruction, State state, String tag) {
        for (int k = 0; k < nmaInstruction.dimensions(); k++) {
            state.stack.removeLast();
        }
        state.stack.addLast(PointsToSet.of(new ObjectReference(nmaInstruction.arrayType().asSymbol(), tag)));
    }
}
