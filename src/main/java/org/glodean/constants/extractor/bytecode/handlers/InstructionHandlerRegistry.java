package org.glodean.constants.extractor.bytecode.handlers;

import com.google.common.collect.ImmutableMap;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.*;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Objects;
import java.util.Queue;

/** Registry that maps instruction classes to their corresponding handlers. */
@SuppressWarnings("NullableProblems")
public final class InstructionHandlerRegistry {
  private final ImmutableMap<Class<? extends Instruction>, InstructionHandler<?>>
      instructionHandlerMap;
  private final ExceptionHandlerLabelHandler exceptionHandlerLabelHandler =
      new ExceptionHandlerLabelHandler();

  private InstructionHandlerRegistry(
      ImmutableMap<Class<? extends Instruction>, InstructionHandler<?>> instructionHandlerMap) {
    this.instructionHandlerMap = Objects.requireNonNull(instructionHandlerMap);
  }

  public static Builder builder() {
    return new Builder();
  }

  public ExceptionHandlerLabelHandler exceptionHandlerLabelHandler() {
    return exceptionHandlerLabelHandler;
  }

  public static final class Builder {
    private final ImmutableMap.Builder<Class<? extends Instruction>, InstructionHandler<?>>
        mapBuilder = ImmutableMap.builder();

    public <T extends Instruction> Builder put(
        Class<T> instructionClass, InstructionHandler<? super T> handler) {
      mapBuilder.put(Objects.requireNonNull(instructionClass), Objects.requireNonNull(handler));
      return this;
    }

    public InstructionHandlerRegistry build() {
      return new InstructionHandlerRegistry(mapBuilder.build());
    }
  }

  public static InstructionHandlerRegistry defaultRegistry() {
    return InstructionHandlerRegistry.builder()
        .put(ArrayLoadInstruction.class, new ArrayLoadHandler())
        .put(ArrayStoreInstruction.class, new ArrayStoreHandler())
        .put(BranchInstruction.class, new BranchHandler())
        .put(ConstantInstruction.class, new ConstantHandler())
        .put(FieldInstruction.class, new FieldHandler())
        .put(IncrementInstruction.class, new IncrementHandler())
        .put(InvokeInstruction.class, new InvokeHandler())
        .put(InvokeDynamicInstruction.class, new InvokeDynamicHandler())
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
  }

  @SuppressWarnings("unchecked")
  public InstructionHandler<? super Instruction> findHandlerFor(Class<?> runtimeClass) {
    if (!Instruction.class.isAssignableFrom(runtimeClass)) {
      return null;
    }
    Class<? extends Instruction> key = (Class<? extends Instruction>) runtimeClass;

    // try exact match first
    InstructionHandler<?> found = instructionHandlerMap.get(key);
    if (found != null) return (InstructionHandler<? super Instruction>) found;

    // BFS over interfaces and superclasses (only consider types assignable to Instruction)
    Queue<Class<?>> toCheck = new ArrayDeque<>();
    Collections.addAll(toCheck, key.getInterfaces());
    Class<?> sup = key.getSuperclass();
    if (sup != null) toCheck.offer(sup);

    while (!toCheck.isEmpty()) {
      Class<?> c = toCheck.poll();
      if (!Instruction.class.isAssignableFrom(c)) continue;
      Class<? extends Instruction> cc = (Class<? extends Instruction>) c;
      found = instructionHandlerMap.get(cc);
      if (found != null) return (InstructionHandler<? super Instruction>) found;
      for (Class<?> interfaceClass : c.getInterfaces()) toCheck.offer(interfaceClass);
      Class<?> sc = c.getSuperclass();
      if (sc != null) toCheck.offer(sc);
    }
    return null;
  }
}
