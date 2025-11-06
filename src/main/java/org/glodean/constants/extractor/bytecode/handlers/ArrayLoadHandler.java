package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

import java.lang.classfile.instruction.ArrayLoadInstruction;

final class ArrayLoadHandler implements InstructionHandler<ArrayLoadInstruction> {
    @Override
    public void handle(ArrayLoadInstruction instruction, State state, String tag) {
        state.stack.removeLast();
        PointsToSet arr = state.stack.removeLast();
        PointsToSet res = new PointsToSet();
        if (arr != null) for (StackAndParameterEntity o : arr) {
            var els = state.arrayElements.get(o);
            if (els != null) {
                res.addAll(els);
            } else {
                res.add(StackAndParameterEntity.convert(((ObjectReference) o).descriptor().componentType(), tag));
            }

        }
        state.stack.addLast(res);
    }
}
