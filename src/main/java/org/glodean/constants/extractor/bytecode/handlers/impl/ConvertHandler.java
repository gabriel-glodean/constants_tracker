package org.glodean.constants.extractor.bytecode.handlers.impl;

import java.lang.classfile.instruction.ConvertInstruction;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.ConvertibleEntity;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for primitive conversion instructions (e.g., I2L, F2D). Performs type conversion on
 * convertible entities at the top of the stack.
 */
final class ConvertHandler implements InstructionHandler<ConvertInstruction> {
  @Override
  public void handle(ConvertInstruction ci, State state, String tag) {
    var newSet = new PointsToSet();
    state.stack.removeLast().stream()
        .map(
            entry ->
                entry instanceof ConvertibleEntity convertibleEntity
                    ? convertibleEntity.convertTo(ci.toType(), tag)
                    : entry)
        .forEach(newSet::add);

    state.stack.addLast(newSet);
  }
}
