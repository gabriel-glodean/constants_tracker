package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.instruction.TableSwitchInstruction;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handler for tableswitch/lookupswitch style dense switch instructions.
 *
 * <p>Conservatively consumes the index/key operand from the stack. Control-flow targets are modeled
 * elsewhere by the successor builder; this handler only models stack effects. TODO: verify
 * correctness for wide switches and ensure stack state matches handler entry points.
 */
final class TableSwitchHandler implements InstructionHandler<TableSwitchInstruction> {
  @Override
  public void handle(TableSwitchInstruction tsi, State state, String tag) {
    // consume key/index
    state.stack.removeLast();
    // no value is pushed
    // TODO: verify correctness (wide tables, default target, etc.)
  }
}
