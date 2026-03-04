package org.glodean.constants.extractor.bytecode.handlers.impl;

import java.lang.classfile.instruction.*;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandlerRegistry;

public final class DefaultRegistrySource {
  private DefaultRegistrySource() {}

  public static InstructionHandlerRegistry defaultRegistry() {
    return InstructionHandlerRegistry.builder()
        .put(ArrayLoadInstruction.class, new ArrayLoadHandler())
        .put(ArrayStoreInstruction.class, new ArrayStoreHandler())
        .put(BranchInstruction.class, new BranchHandler())
        .put(ConstantInstruction.class, new ConstantHandler())
        .put(ConvertInstruction.class, new ConvertHandler())
        .put(DiscontinuedInstruction.class, new DiscontinuedInstructionHandler())
        .put(FieldInstruction.class, new FieldHandler())
        .put(IncrementInstruction.class, new IncrementHandler())
        .put(InvokeInstruction.class, new InvokeHandler())
        .put(InvokeDynamicInstruction.class, new InvokeDynamicHandler())
        .put(LoadInstruction.class, new LoadHandler())
        .put(LookupSwitchInstruction.class, new LookupSwitchHandler())
        .put(MonitorInstruction.class, new MonitorInstructionHandler())
        .put(NewMultiArrayInstruction.class, new NewMultiArrayHandler())
        .put(NewObjectInstruction.class, new NewObjectIHandler())
        .put(NewPrimitiveArrayInstruction.class, new NewPrimitiveArrayHandler())
        .put(NewReferenceArrayInstruction.class, new NewReferenceArrayHandler())
        .put(NopInstruction.class, new NopHandler())
        .put(OperatorInstruction.class, new OperatorHandler())
        .put(ReturnInstruction.class, new ReturnHandler())
        .put(StackInstruction.class, new StackHandler())
        .put(StoreInstruction.class, new StoreHandler())
        .put(TableSwitchInstruction.class, new TableSwitchHandler())
        .put(ThrowInstruction.class, new ThrowHandler())
        .put(TypeCheckInstruction.class, new TypeCheckHandler())
        .build();
  }
}
