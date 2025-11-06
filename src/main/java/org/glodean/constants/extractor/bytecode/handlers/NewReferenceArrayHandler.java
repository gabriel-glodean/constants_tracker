package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

import java.lang.classfile.instruction.NewReferenceArrayInstruction;

final class NewReferenceArrayHandler implements InstructionHandler<NewReferenceArrayInstruction> {
    @Override
    public void handle(NewReferenceArrayInstruction nai, State state, String tag) {
        state.stack.removeLast();
        state.stack.addLast(PointsToSet.of(new ObjectReference(nai.componentType().asSymbol(), tag)));
    }
}
