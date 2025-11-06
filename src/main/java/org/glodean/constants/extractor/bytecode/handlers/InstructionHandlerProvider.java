package org.glodean.constants.extractor.bytecode.handlers;

import com.google.common.collect.ImmutableMap;

import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.*;
import java.util.Map;

public enum InstructionHandlerProvider {
    PROVIDER;
    private final Map<Class<? extends Instruction>, InstructionHandler<?>> instructionHandlerMap =
            ImmutableMap.<Class<? extends Instruction>, InstructionHandler<?>>builder()
                    .put(ArrayLoadInstruction.class, new ArrayLoadHandler())
                    .put(ArrayStoreInstruction.class, new ArrayStoreHandler())
                    .put(BranchInstruction.class, new BranchHandler())
                    .put(ConstantInstruction.class, new ConstantHandler())
                    .put(FieldInstruction.class, new FieldHandler())
                    .put(IncrementInstruction.class, new IncrementHandler())
                    .put(InvokeInstruction.class, new InvokeHandler())
                    .put(LoadInstruction.class, new LoadHandler())
                    .put(NewMultiArrayInstruction.class, new NewMultiArrayHandler())
                    .put(NewObjectInstruction.class, new NewObjectIHandler())
                    .put(NewPrimitiveArrayInstruction.class, new NewPrimitiveArrayHandler())
                    .put(NewReferenceArrayInstruction.class, new NewReferenceArrayHandler())
                    .put(OperatorInstruction.class, new OperatorHandler())
                    .put(ReturnInstruction.class, new ReturnHandler())
                    .put(StackInstruction.class, new StackHandler())
                    .put(StoreInstruction.class, new StoreHandler())
                    .put(TypeCheckInstruction.class, new TypeCheckHandler())
                    .build();

    @SuppressWarnings("unchecked")
    public <IT extends Instruction> InstructionHandler<IT> handlerFor(Class<IT> clazz) {
        return (InstructionHandler<IT>) instructionHandlerMap.get(clazz);
    }
}
