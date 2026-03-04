package org.glodean.constants.extractor.bytecode.handlers.impl;

import static java.lang.constant.ConstantDescs.CD_void;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.PrimitiveValue;
import org.glodean.constants.extractor.bytecode.types.State;

final class InvokeHandler implements InstructionHandler<InvokeInstruction> {
  @Override
  public void handle(InvokeInstruction ii, State state, String tag) {
    int argCount = ii.typeSymbol().parameterCount();
    for (int k = 0; k < argCount; k++) state.stack.removeLast();
    if (ii.opcode() != Opcode.INVOKESTATIC) state.stack.removeLast();
    var returnType = ii.typeSymbol().returnType();
    if (returnType != CD_void) {
      if (returnType.isPrimitive()) {
        state.stack.addLast(PointsToSet.of(new PrimitiveValue(returnType, tag)));
        return;
      }
      state.stack.addLast(PointsToSet.of(new ObjectReference(returnType, tag)));
    }
  }
}
