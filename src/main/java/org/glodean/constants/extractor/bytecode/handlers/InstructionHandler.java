package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.Instruction;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Interface for handling bytecode instructions of type {@code IT}.
 *
 * @param <IT> the instruction type
 */
public sealed interface InstructionHandler<IT extends Instruction>
    permits ArrayLoadHandler,
        ArrayStoreHandler,
        BranchHandler,
        ConstantHandler,
        ConvertHandler,
        FieldHandler,
        IncrementHandler,
        InvokeDynamicHandler,
        InvokeHandler,
        LoadHandler,
        LookupSwitchHandler,
        MonitorInstructionHandler,
        NewMultiArrayHandler,
        NewObjectIHandler,
        NewPrimitiveArrayHandler,
        NewReferenceArrayHandler,
        NopHandler,
        OperatorHandler,
        ReturnHandler,
        StackHandler,
        StoreHandler,
        TableSwitchHandler,
        ThrowHandler,
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
