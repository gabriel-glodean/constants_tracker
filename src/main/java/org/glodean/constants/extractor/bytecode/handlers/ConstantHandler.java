package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.NumericConstant;
import org.glodean.constants.extractor.bytecode.types.ObjectConstant;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

import java.lang.classfile.instruction.ConstantInstruction;

final class ConstantHandler implements InstructionHandler<ConstantInstruction> {
    @Override
    public void handle(ConstantInstruction ci, State state, String tag) {
        var value = ci.constantValue();
        state.stack.addLast(PointsToSet.of(
                value instanceof Number number ? new NumericConstant(number) : new ObjectConstant(value)));
    }
}
