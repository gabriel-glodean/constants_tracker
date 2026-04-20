package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.Instruction;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Interface for handling bytecode instructions of type {@code IT}.
 *
 * <p>The bytecode analyzer uses a registry of instruction handlers to model the effect
 * of each JVM instruction on the abstract state. Each handler:
 * <ul>
 *   <li>Reads values from the operand stack and/or local variables</li>
 *   <li>Updates the stack, locals, heap, or statics according to instruction semantics</li>
 *   <li>Maintains conservative approximations (over-approximating possible values)</li>
 * </ul>
 *
 * <p><b>Example:</b> An {@code IADD} handler pops two values from the stack, and if both
 * are {@link org.glodean.constants.extractor.bytecode.types.NumericConstant}s, pushes
 * their sum; otherwise pushes a generic numeric reference.
 *
 * @param <IT> the instruction type (e.g., {@link java.lang.classfile.instruction.LoadInstruction})
 * @see InstructionHandlerRegistry
 */
public interface InstructionHandler<IT extends Instruction> {

  /**
   * Handles the given instruction and updates the abstract JVM state.
   *
   * <p><b>IMPORTANT:</b> This method mutates {@code state} in-place to model the instruction's
   * effect. Handlers should not create new State objects—they operate on the provided state.
   *
   * @param instruction the instruction to handle
   * @param state the current bytecode state (mutated by this method)
   * @param tag an optional context tag (e.g., method name) for debugging
   */
  void handle(IT instruction, State state, String tag);
}
