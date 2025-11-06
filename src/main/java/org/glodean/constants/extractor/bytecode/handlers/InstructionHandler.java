package org.glodean.constants.extractor.bytecode.handlers;

import org.glodean.constants.extractor.bytecode.types.State;

import java.lang.classfile.Instruction;

public sealed interface InstructionHandler<IT extends Instruction>
        permits ArrayLoadHandler, ArrayStoreHandler, BranchHandler, ConstantHandler, FieldHandler, IncrementHandler, InvokeHandler, LoadHandler, NewMultiArrayHandler, NewObjectIHandler, NewPrimitiveArrayHandler, NewReferenceArrayHandler, OperatorHandler, ReturnHandler, StackHandler, StoreHandler, TypeCheckHandler {
    void handle(IT instruction, State state, String tag);
}
