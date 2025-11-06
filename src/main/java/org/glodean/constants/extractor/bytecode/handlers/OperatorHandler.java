package org.glodean.constants.extractor.bytecode.handlers;

import com.google.common.collect.Streams;
import org.glodean.constants.extractor.bytecode.types.*;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.OperatorInstruction;

import static java.lang.constant.ConstantDescs.CD_int;

final class OperatorHandler implements InstructionHandler<OperatorInstruction> {
    @Override
    public void handle(OperatorInstruction oi, State state, String tag) {
        if (oi.opcode() == Opcode.ARRAYLENGTH) {
            state.stack.removeLast();
            state.stack.addLast(PointsToSet.of(new PrimitiveValue(CD_int, tag)));
        } else {
            var pointsToSet = new PointsToSet();
            Streams.forEachPair(state.stack.removeLast().stream(), state.stack.removeLast().stream(),
                    (first, second) ->
                            pointsToSet.add(((ConstantPropagatingEntity) first).propagate((ConstantPropagatingEntity) second))
            );
            state.stack.addLast(pointsToSet);
        }
    }
}
