package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.Instruction;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Interface for handling bytecode instructions of type {@code IT}.
 *
 * @param <IT> the instruction type
 */
public sealed interface InstructionHandler<IT extends Instruction>
    permits InvokeDynamicHandler,
        ArrayLoadHandler,
        ArrayStoreHandler,
        BranchHandler,
        ConstantHandler,
        FieldHandler,
        IncrementHandler,
        InvokeHandler,
        LoadHandler,
        NewMultiArrayHandler,
        NewObjectIHandler,
        NewPrimitiveArrayHandler,
        NewReferenceArrayHandler,
        OperatorHandler,
        ReturnHandler,
        StackHandler,
        StoreHandler,
        TypeCheckHandler {

  /**
   * Handles the given instruction and updates the abstract JVN state.
   *
   * @param instruction the instruction to handle
   * @param state the current bytecode state
   * @param tag an optional tag for context
   */
  void handle(IT instruction, State state, String tag);
}
