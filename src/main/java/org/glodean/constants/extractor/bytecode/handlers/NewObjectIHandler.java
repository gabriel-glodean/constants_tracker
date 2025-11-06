package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

import java.lang.classfile.instruction.NewObjectInstruction;

final class NewObjectIHandler implements InstructionHandler<NewObjectInstruction> {
    @Override
    public void handle(NewObjectInstruction ni, State state, String tag) {
        StackAndParameterEntity o = new ObjectReference(ni.className().asSymbol(), tag);
        state.stack.addLast(PointsToSet.of(o));
    }
}
