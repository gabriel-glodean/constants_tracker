package org.glodean.constants.extractor.bytecode.handlers;

import com.google.common.collect.ImmutableMap;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Registry that maps instruction classes to their corresponding handlers. */
@SuppressWarnings("NullableProblems")
public final class InstructionHandlerRegistry {
  private final ImmutableMap<Class<? extends Instruction>, InstructionHandler<?>>
      instructionHandlerMap;
  private final Map<Class<? extends Instruction>, InstructionHandler<?>> cache =
      new ConcurrentHashMap<>();

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
  public InstructionHandler<? super Instruction> findHandlerFor(
      Class<? extends Instruction> runtimeClass) {
    // quick null/assignable guard (caller now should pass a subclass of Instruction)
    if (runtimeClass == null) return null;

    // check cache first
    InstructionHandler<?> cached = cache.get(runtimeClass);
    if (cached != null) {
      return (InstructionHandler<? super Instruction>) cached;
    }

    // try exact match first
    InstructionHandler<?> found = instructionHandlerMap.get(runtimeClass);
    if (found == null) {
      // BFS over interfaces and superclasses (only consider types assignable to Instruction)
      Queue<Class<?>> toCheck = new ArrayDeque<>();
      Collections.addAll(toCheck, runtimeClass.getInterfaces());
      Class<?> sup = runtimeClass.getSuperclass();
      if (sup != null) toCheck.offer(sup);
      Set<Class<?>> visited = new HashSet<>();
      while (!toCheck.isEmpty()) {
        Class<?> c = toCheck.poll();
        if (!visited.add(c)) continue;
        if (!Instruction.class.isAssignableFrom(c)) continue;
        @SuppressWarnings("unchecked")
        Class<? extends Instruction> cc = (Class<? extends Instruction>) c;
        found = instructionHandlerMap.get(cc);
        if (found != null) break;
        for (Class<?> interfaceClass : c.getInterfaces()) toCheck.offer(interfaceClass);
        Class<?> sc = c.getSuperclass();
        if (sc != null) toCheck.offer(sc);
      }
    }

    // cache only positive results to avoid storing nulls
    if (found != null) {
      cache.put(runtimeClass, found);
    }
    return (InstructionHandler<? super Instruction>) found;
  }
}
