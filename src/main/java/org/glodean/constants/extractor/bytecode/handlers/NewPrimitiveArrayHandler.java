package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;

final class NewPrimitiveArrayHandler implements InstructionHandler<NewPrimitiveArrayInstruction>{
    @Override
    public void handle(NewPrimitiveArrayInstruction nai, State state, String tag) {
        state.stack.removeLast();
        state.stack.addLast(PointsToSet.of(new ObjectReference(nai.typeKind().upperBound(), tag)));
    }
}
