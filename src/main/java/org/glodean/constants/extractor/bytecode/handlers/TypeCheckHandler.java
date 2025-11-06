package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.PrimitiveValue;
import org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity;
import org.glodean.constants.extractor.bytecode.types.State;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.TypeCheckInstruction;

final class TypeCheckHandler implements InstructionHandler<TypeCheckInstruction> {
    @Override
    public void handle(TypeCheckInstruction tc, State state, String tag) {
        if (tc.opcode() == Opcode.INSTANCEOF) {
            state.stack.removeLast();
            state.stack.addLast(
                    PointsToSet.of(
                            StackAndParameterEntity.convert(tc.type().asSymbol(), tag)));
        }
    }
}
