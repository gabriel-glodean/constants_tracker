package org.glodean.constants.extractor.bytecode;

import com.google.common.collect.ImmutableMap;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a conservative control-flow successor mapping for a method body represented as a list of
 * {@link java.lang.classfile.CodeElement} instances.
 *
 * <p>This helper computes, for every instruction index, a list of successor instruction indices.
 * Successors are determined using JVM-like control-flow rules: - fall-through to the next
 * instruction (i + 1) unless the instruction is a terminal (return) or an unconditional GOTO, -
 * explicit branch targets are resolved by locating a LabelTarget with a matching Label, -
 * exception-handler edges are added conservatively by reflecting the provided exception table.
 *
 * <p>Important notes and assumptions: - The input `code` list is assumed to be in instruction order
 * and to contain label target elements (instances comparable via Label equality). - The algorithm
 * is intentionally defensive: it aims to run across different JDKs / class file APIs and will avoid
 * throwing for missing/unknown elements. As a result it may produce conservative results (extra
 * edges) or include unresolved indices (e.g. -1) when labels cannot be resolved. - ThrowInstruction
 * is treated as a terminal instruction: it does not fall through to the next instruction and will
 * not be given additional exception-handler successor edges by the handler pass. This choice makes
 * the successor graph conservative with respect to exception handling (handlers are only attached
 * to normal instructions covered by try ranges). - Thread-safety: methods are static and stateless.
 * Callers must not concurrently mutate the provided lists while this helper is executing.
 */
final class SuccessorBuilder {
  private SuccessorBuilder() {}

  /**
   * Compute successor lists for the provided code elements.
   *
   * <p>For each instruction index i the returned outer list contains a List<Integer> of indices
   * that represent possible program counters reachable from index i. The method applies these
   * rules: - Adds i + 1 as a successor for normal fall-through instructions (unless the instruction
   * is a ReturnInstruction or an unconditional GOTO). - For BranchInstruction instances the branch
   * target index is resolved via indexOfLabel(...) and added as a successor. If the target cannot
   * be resolved indexOfLabel(...) returns -1; that value will be present in the successor list and
   * callers should treat it as unresolved. - ThrowInstruction is treated as terminal: it does not
   * fall through to i+1, and it will not receive handler successors in the exception-handler pass.
   * - Finally, a conservative set of exception-handler successors is added using the provided
   * exceptionCatches list, but throws are excluded from receiving those handler edges.
   *
   * @param code list of CodeElement representing the method's code (in instruction order)
   * @param exceptionCatches exception table entries to reflect exception edges; may be null or
   *     empty (null is treated as empty)
   * @return a Successors record containing the successor lists and a map of handler Labels to their
   *     catch types
   */
  public static Successors build(List<CodeElement> code, List<ExceptionCatch> exceptionCatches) {
    List<List<Integer>> successors = new ArrayList<>();
    for (int i = 0; i < code.size(); i++) successors.add(new ArrayList<>());

    for (int i = 0; i < code.size(); i++) {
      CodeElement element = code.get(i);
      if (element instanceof ReturnInstruction || element instanceof ThrowInstruction) {
        // no successors for returns or throws, assuming no one is catching its own throw
        continue;
      }
      if (element instanceof BranchInstruction bi) {
        if (bi.opcode() != Opcode.GOTO && bi.opcode() != Opcode.GOTO_W) {
          successors.get(i).add(i + 1);
        }
        int nextInstruction = indexOfLabel(bi.target(), code);
        successors.get(i).add(nextInstruction);
      } else {
        successors.get(i).add(i + 1);
      }
    }

    return new Successors(successors, addHandlerSuccessors(code, exceptionCatches, successors));
  }

  record Successors(List<List<Integer>> successors, Map<Label, ClassDesc> handlerStarts) {}

  /**
   * Best-effort: map exception-table handlers (if present) to handler indices and add the handler
   * as a successor for any instruction in the try range.
   *
   * <p>This method is conservative: for each ExceptionCatch entry it locates the handler index and
   * then adds an edge from every instruction in [tryStart, tryEnd]* to that handler (except for
   * ReturnInstruction and ThrowInstruction which are treated as terminal here). If any label cannot
   * be resolved, the corresponding mapping is skipped. The method tolerates a null or empty
   * exceptionCatches list.
   *
   * @param code the method's code list (instruction order)
   * @param exceptionCatches exception table entries; may be null or empty
   * @param successors the mutable successor lists to populate; must be parallel to `code`
   * @return a map of handler Labels to their catch types
   */
  private static Map<Label, ClassDesc> addHandlerSuccessors(
      List<CodeElement> code,
      List<ExceptionCatch> exceptionCatches,
      List<List<Integer>> successors) {
    if (exceptionCatches == null) return Map.of();
    var handlerMap = new HashMap<Label, ClassDesc>();
    for (ExceptionCatch exceptionCatch : exceptionCatches) {
      handlerMap.put(
          exceptionCatch.handler(),
          exceptionCatch
              .catchType()
              .map(ClassEntry::asSymbol)
              .orElseGet(() -> ClassDesc.ofInternalName("java/lang/Throwable")));
      int handlerIdx = indexOfLabel(exceptionCatch.handler(), code);
      if (handlerIdx <= 0) {
        continue;
      }
      int startIdx = indexOfLabel(exceptionCatch.tryStart(), code);
      int endIdx = indexOfLabel(exceptionCatch.tryEnd(), code);

      for (int j = Math.max(0, startIdx); j < Math.min(endIdx, code.size()); j++) {
        var elem = code.get(j);
        if (elem instanceof ReturnInstruction) continue;
        if (elem instanceof ThrowInstruction)
          continue; // throws are terminal; do not attach handler edges
        List<Integer> successor = successors.get(j);
        if (!successor.contains(handlerIdx)) successor.add(handlerIdx);
      }
    }
    return ImmutableMap.copyOf(handlerMap);
  }

  /**
   * Locate the index of a LabelTarget whose label equals the provided Label.
   *
   * @param t the Label to search for
   * @param code the code list to search (instruction order)
   * @return the index of the matching LabelTarget, or -1 if none found
   */
  private static int indexOfLabel(Label t, List<CodeElement> code) {
    for (int i = 0; i < code.size(); i++)
      if (code.get(i) instanceof LabelTarget lt && lt.label().equals(t)) return i;
    return -1;
  }
}
