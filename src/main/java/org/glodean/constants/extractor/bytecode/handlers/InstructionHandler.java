package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.Instruction;
import org.glodean.constants.extractor.bytecode.types.State;

public sealed interface InstructionHandler<IT extends Instruction>
    permits ArrayLoadHandler,
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
  void handle(IT instruction, State state, String tag);
}
