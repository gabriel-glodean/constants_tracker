package org.glodean.constants.extractor.bytecode;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper to build control-flow successors for a method's code elements. This includes a
 * conservative addition of exception-handler edges by reflecting an exception table when available
 * on the code attribute. The implementation is best-effort and will not throw when running on
 * different JDKs with different java.lang.classfile APIs.
 */
final class SuccessorBuilder {
  private SuccessorBuilder() {}

  public static List<List<Integer>> build(List<CodeElement> code, MethodModel methodModel) {
    List<List<Integer>> successors = new ArrayList<>();
    for (int i = 0; i < code.size(); i++) successors.add(new ArrayList<>());

    for (int i = 0; i < code.size(); i++) {
      CodeElement element = code.get(i);
      if (element instanceof ReturnInstruction || element instanceof ThrowInstruction) {
        // no successors for returns/throws
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

    // Best-effort: map exception-table handlers (if present) to handler indices and add
    // handler as a successor for any instruction in the try range.
    try {
      var codeAttrOpt = methodModel.findAttribute(java.lang.classfile.Attributes.code());
      if (codeAttrOpt.isPresent()) {
        Object codeAttr = codeAttrOpt.get();
        var exMethod = codeAttr.getClass().getMethod("exceptionTable");
        @SuppressWarnings("unchecked")
        List<Object> handlers = (List<Object>) exMethod.invoke(codeAttr);
        if (handlers != null) {
          for (Object h : handlers) {
            Label startLbl = null;
            Label endLbl = null;
            Label handlerLbl = null;
            try {
              var m = h.getClass().getMethod("start");
              Object o = m.invoke(h);
              if (o instanceof Label) startLbl = (Label) o;
            } catch (ReflectiveOperationException ignore) {
            }
            try {
              var m = h.getClass().getMethod("end");
              Object o = m.invoke(h);
              if (o instanceof Label) endLbl = (Label) o;
            } catch (ReflectiveOperationException ignore) {
            }
            try {
              var m = h.getClass().getMethod("handler");
              Object o = m.invoke(h);
              if (o instanceof Label) handlerLbl = (Label) o;
            } catch (ReflectiveOperationException ignore) {
            }

            if (handlerLbl == null) continue;
            int handlerIdx = indexOfLabel(handlerLbl, code);
            if (handlerIdx < 0) continue;

            int startIdx = 0;
            int endIdx = code.size();
            if (startLbl != null) {
              int s = indexOfLabel(startLbl, code);
              if (s >= 0) startIdx = s;
            }
            if (endLbl != null) {
              int e = indexOfLabel(endLbl, code);
              if (e >= 0) endIdx = e;
            }

            for (int j = Math.max(0, startIdx); j < Math.min(endIdx, code.size()); j++) {
              var elem = code.get(j);
              if (elem instanceof ReturnInstruction || elem instanceof ThrowInstruction) continue;
              List<Integer> succ = successors.get(j);
              if (!succ.contains(handlerIdx)) succ.add(handlerIdx);
            }
          }
        }
      }
    } catch (ReflectiveOperationException | ClassCastException ex) {
      // best-effort: ignore and continue
    }

    return successors;
  }

  private static int indexOfLabel(Label t, List<CodeElement> code) {
    for (int i = 0; i < code.size(); i++)
      if (code.get(i) instanceof java.lang.classfile.instruction.LabelTarget lt
          && lt.label().equals(t)) return i;
    return -1;
  }
}
