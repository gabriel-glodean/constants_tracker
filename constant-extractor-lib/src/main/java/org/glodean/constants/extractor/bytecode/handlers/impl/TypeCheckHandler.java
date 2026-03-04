package org.glodean.constants.extractor.bytecode.handlers.impl;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.TypeCheckInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for type-checking instructions such as INSTANCEOF and CHECKCAST. Models the stack effects
 * and pushes a typed value for instanceof results.
 */
final class TypeCheckHandler implements InstructionHandler<TypeCheckInstruction> {
  @Override
  public void handle(TypeCheckInstruction tc, State state, String tag) {
    if (tc.opcode() == Opcode.INSTANCEOF) {
      state.stack.removeLast();
      state.stack.addLast(
          PointsToSet.of(StackAndParameterEntity.convert(tc.type().asSymbol(), tag)));
    }
  }
}
