package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.*;

import java.lang.classfile.instruction.IncrementInstruction;

final class IncrementHandler implements InstructionHandler<IncrementInstruction> {
    @Override
    public void handle(IncrementInstruction ii, State state, String tag) {
        int var = ii.slot();
        PointsToSet local = state.locals.get(var);
        PointsToSet newSet = new PointsToSet();
        NumericConstant constant = new NumericConstant(ii.constant());
        for (var value : local) {
            newSet.add(((ConstantPropagatingEntity) value).propagate(constant));
        }
        state.locals.set(var, newSet);
    }
}
