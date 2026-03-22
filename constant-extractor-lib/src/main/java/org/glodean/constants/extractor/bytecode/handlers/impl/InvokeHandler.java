package org.glodean.constants.extractor.bytecode.handlers.impl;

import static java.lang.constant.ConstantDescs.CD_void;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.PrimitiveValue;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for regular method invocation instructions
 * ({@code invokevirtual}, {@code invokespecial}, {@code invokestatic}, {@code invokeinterface}).
 *
 * <p>Pops all arguments from the stack (plus the receiver object for non-static calls) and,
 * if the method has a non-void return type, pushes a fresh typed reference for the result.
 * Return values are conservatively modelled as unknown (a new allocation-site reference),
 * since inter-procedural analysis is out of scope.
 */
final class InvokeHandler implements InstructionHandler<InvokeInstruction> {
  /**
   * {@inheritDoc}
   *
   * <p>Pops {@code argCount} operands, then pops the receiver for non-{@code INVOKESTATIC}
   * calls. Pushes a {@link PrimitiveValue} or {@link ObjectReference} for non-void returns.
   */
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
