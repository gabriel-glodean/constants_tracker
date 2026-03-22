package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.constant.ClassDesc;
import java.util.Set;
import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Models the abstract stack effect when control enters an exception handler.
 *
 * <p>At a handler entry point the JVM guarantees that the operand stack contains exactly
 * one element — the thrown exception reference. This handler clears the current stack and
 * pushes a fresh {@link ObjectReference} whose type is the common supertype of all
 * {@code catch} types in the handler's catch table.
 */
public class ExceptionHandlerLabelHandler {

  /**
   * Clears the operand stack and pushes a conservative exception reference.
   *
   * <p>If there is exactly one catch type, the reference uses that type; otherwise
   * it falls back to {@code java/lang/Throwable}.
   *
   * @param catchTypes the set of exception types covered by this handler
   * @param state      the abstract state to update (stack is cleared and repopulated)
   * @param tag        allocation-site tag used to identify the synthetic reference
   */
  public void handle(Set<ClassDesc> catchTypes, State state, String tag) {
    state.stack.clear();
    ClassDesc catchType =
        catchTypes.size() == 1
            ? catchTypes.iterator().next()
            : ClassDesc.ofInternalName("java/lang/Throwable");
    state.stack.addLast(PointsToSet.of(new ObjectReference(catchType, tag)));
  }
}
