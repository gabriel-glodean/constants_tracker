package org.glodean.constants.extractor.bytecode.handlers;

import com.google.common.collect.ImmutableMap;
import java.lang.classfile.Instruction;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that maps instruction classes to their corresponding handlers.
 *
 * <p>This registry provides efficient lookup of {@link InstructionHandler}s based on
 * runtime instruction types. It supports:
 * <ul>
 *   <li>Direct class-to-handler mappings (registered via builder)</li>
 *   <li>Inheritance-based lookup (finds handler for superclass if exact match not found)</li>
 *   <li>Concurrent caching (avoids repeated reflection-based searches)</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> The registry is immutable after construction. The internal cache
 * uses {@link ConcurrentHashMap} for thread-safe handler resolution during parallel analysis.
 *
 * <p><b>Example usage:</b>
 * <pre>{@code
 * InstructionHandlerRegistry registry = InstructionHandlerRegistry.builder()
 *     .put(LoadInstruction.class, new LoadHandler())
 *     .put(StoreInstruction.class, new StoreHandler())
 *     .build();
 * InstructionHandler handler = registry.findHandlerFor(instruction.getClass());
 * }</pre>
 */
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

  /**
   * Creates a new {@link Builder} for constructing an {@code InstructionHandlerRegistry}.
   *
   * @return a fresh, empty builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the singleton {@link ExceptionHandlerLabelHandler} used to model the stack
   * effect at exception-handler entry points.
   *
   * @return the exception handler label handler
   */
  public ExceptionHandlerLabelHandler exceptionHandlerLabelHandler() {
    return exceptionHandlerLabelHandler;
  }

  public static final class Builder {
    private final ImmutableMap.Builder<Class<? extends Instruction>, InstructionHandler<?>>
        mapBuilder = ImmutableMap.builder();

    /**
     * Associates the given {@link InstructionHandler} with the specified instruction class.
     *
     * @param <T>              the concrete instruction subtype
     * @param instructionClass the instruction interface or class to handle
     * @param handler          the handler to register
     * @return this builder for chaining
     * @throws NullPointerException if either argument is {@code null}
     */
    public <T extends Instruction> Builder put(
        Class<T> instructionClass, InstructionHandler<? super T> handler) {
      mapBuilder.put(Objects.requireNonNull(instructionClass), Objects.requireNonNull(handler));
      return this;
    }

    /**
     * Builds an immutable {@link InstructionHandlerRegistry} from all registered mappings.
     *
     * @return a new, immutable {@code InstructionHandlerRegistry}
     */
    public InstructionHandlerRegistry build() {
      return new InstructionHandlerRegistry(mapBuilder.build());
    }
  }

  /**
   * Finds the best-matching {@link InstructionHandler} for the given runtime instruction class.
   *
   * <p>First tries an exact match; if none is found, performs a BFS over the class hierarchy
   * (interfaces and superclasses) looking for a registered handler. Results of successful
   * lookups are cached for subsequent calls.
   *
   * @param runtimeClass the concrete runtime class of the instruction
   * @return the matching handler, or {@code null} if no handler is registered for the type
   */
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
