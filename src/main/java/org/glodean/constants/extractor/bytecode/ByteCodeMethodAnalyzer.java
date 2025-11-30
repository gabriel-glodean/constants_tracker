package org.glodean.constants.extractor.bytecode;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.*;
import java.util.stream.Collectors;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandlerRegistry;
import org.glodean.constants.extractor.bytecode.handlers.impl.DefaultRegistrySource;
import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Performs a per-method bytecode analysis producing IN/OUT abstract states and a list of discovered
 * method calls.
 *
 * <p>The analyzer builds a conservative control-flow graph, then runs a worklist algorithm to
 * compute per-instruction abstract states using the configured {@link InstructionHandlerRegistry}.
 * The resulting {@code in} and {@code out} lists are public fields used by the merger to extract
 * constant usage information.
 */
final class ByteCodeMethodAnalyzer {
  final ClassModel cm;
  final MethodModel methodModel;
  final String methodTag;
  final List<State> in = new ArrayList<>();
  final List<State> out = new ArrayList<>();
  final List<List<Integer>> successors = new ArrayList<>();
  final List<CodeElement> code;
  final int maxLocals;
  final List<String> calls = new ArrayList<>();
  private final InstructionHandlerRegistry instructionHandlerRegistry;
  private final Map<Label, Set<ClassDesc>> exceptionHandlerStarts;

  ByteCodeMethodAnalyzer(ClassModel cm, MethodModel mm) {
    this(cm, mm, DefaultRegistrySource.defaultRegistry());
  }

  ByteCodeMethodAnalyzer(ClassModel cm, MethodModel mm, InstructionHandlerRegistry registry) {
    this.cm = cm;
    this.methodModel = mm;
    this.instructionHandlerRegistry = registry;
    this.methodTag =
        cm.thisClass().asInternalName()
            + "::"
            + mm.methodName().stringValue()
            + mm.methodType().stringValue();
    var attributes = mm.findAttribute(Attributes.code());
    this.maxLocals = attributes.map(CodeAttribute::maxLocals).orElse(0);
    CodeModel codeModel =
        mm.elementStream()
            .filter(e -> e instanceof CodeModel)
            .map(e -> (CodeModel) e)
            .findFirst()
            .orElse(null);
    if (codeModel == null) {
      this.code = List.of();
      this.exceptionHandlerStarts = Map.of();
      return;
    }
    this.code = codeModel.elementList();
    for (int i = 0; i < code.size(); i++) {
      in.add(null);
      out.add(null);
      successors.add(new ArrayList<>());
    }
    // delegate successor construction to SuccessorBuilder
    var successorRecord = SuccessorBuilder.build(code, codeModel.exceptionHandlers());
    var built = successorRecord.successors();
    this.exceptionHandlerStarts = successorRecord.handlerStarts();
    for (int i = 0; i < built.size() && i < successors.size(); i++) {
      successors.set(i, built.get(i));
    }
  }

  public void run() throws ModelExtractor.ExtractionException {
    if (code.isEmpty()) return;
    var work = new ArrayDeque<Integer>();
    State entry = new State(maxLocals);
    int index = 0;
    if (!methodModel.flags().has(AccessFlag.STATIC)) {
      entry.locals.set(
          0,
          PointsToSet.of(
              new ObjectReference(
                  cm.thisClass().asSymbol(), cm.thisClass().asInternalName() + "::<this>")));
      index++;
    }

    String tag = "Param";
    for (var paramType : methodModel.methodTypeSymbol().parameterList()) {
      entry.locals.set(
          index, PointsToSet.of(StackAndParameterEntity.convert(paramType, tag + "#" + index)));
      index++;
    }

    in.set(0, entry.copy());
    out.set(0, entry.copy());
    work.add(0);

    while (!work.isEmpty()) {
      int i = work.removeFirst();
      State in = this.in.get(i);
      State out = this.out.get(i);
      if (in == null) { // can happen if multiple predecessors; skip until we have an IN
        continue;
      }
      if (out == null) this.out.set(i, out = new State(in.locals.size()));
      // 1) Compute candidate OUT from current IN (identity for nop/labels/line)
      State sOut = transfer(i, in.copy());
      // 2) Grow OUT[i] by union with sOut
      out.unionWith(sOut);
      // 3) For each successor s, try to grow IN[s] by OUT[i]; enqueue on growth
      for (int s : successors.get(i)) {
        if (s < 0 || s >= code.size()) continue;
        State inS = this.in.get(s);
        if (inS == null) {
          this.in.set(s, out.copy());
          work.add(s);
        } else if (inS.unionWith(out)) { // <-- union-based IN growth
          work.add(s);
        }
      }
    }
  }

  private State transfer(int i, State st) throws ModelExtractor.ExtractionException {
    var e = code.get(i);
    var tag = methodTag + "@" + i;
    if (e instanceof Instruction ins) {
      Class<? extends Instruction> runtime = ins.getClass();
      var handler = instructionHandlerRegistry.findHandlerFor(runtime);
      if (handler == null) {
        throw new ModelExtractor.ExtractionException(
            "No handler for instruction: " + runtime.getName());
      }
      handler.handle(ins, st, tag);
      recordInvokeIfNeeded(ins);
      return st;
    }
    if (e instanceof Label label) {
      var catchTypeOpt = exceptionHandlerStarts.get(label);
      if (catchTypeOpt != null) {
        instructionHandlerRegistry.exceptionHandlerLabelHandler().handle(catchTypeOpt, st, tag);
      }
    }
    return st;
  }

  private void recordInvokeIfNeeded(Instruction ins) {
    if (ins instanceof InvokeInstruction ii) {
      calls.add(
          methodTag
              + " -> "
              + ii.owner().asInternalName()
              + "."
              + ii.name().stringValue()
              + ii.type().stringValue());
    }
  }

  public String report() {
    StringBuilder sb = new StringBuilder();
    sb.append("\n=== Analysis Report for ").append(methodTag).append(" ===\n");
    for (int i = 0; i < code.size(); i++) {
      var e = code.get(i);
      sb.append("%4d: %-30s\n".formatted(i, pretty(e)));
      State sIn = in.get(i), sOut = out.get(i);
      if (sIn != null) sb.append("     IN  ").append(stringify(sIn)).append('\n');
      if (sOut != null) sb.append("     OUT ").append(stringify(sOut)).append('\n');
    }
    if (!calls.isEmpty()) {
      sb.append("  Calls:").append('\n');
      calls.forEach(c -> sb.append("    ").append(c).append('\n'));
    }
    return sb.toString();
  }

  String pretty(CodeElement e) {
    if (e instanceof Instruction ins) return ins.opcode().name();
    return e.getClass().getSimpleName();
  }

  String stringify(State s) {
    StringBuilder sb = new StringBuilder();
    sb.append("Locals:");
    for (int i = 0; i < s.locals.size(); i++)
      sb.append(" ").append(i).append("=").append(pt(s.locals.get(i)));
    sb.append(" | Stack:");
    for (PointsToSet slot : s.stack) sb.append(" [").append(pt(slot)).append("]");
    sb.append(" | Heap:");
    s.heap.forEach((k, v) -> sb.append(" ").append(k).append("=").append(pt(v)));
    sb.append(" | Statics:");
    s.statics.forEach((k, v) -> sb.append(" ").append(k).append("=").append(pt(v)));
    sb.append(" | Arrays:");
    s.arrayElements.forEach((k, v) -> sb.append(" ").append(k).append("[*]=").append(pt(v)));
    return sb.toString();
  }

  String pt(PointsToSet s) {
    return s == null
        ? "-"
        : s.stream().map(Object::toString).sorted().collect(Collectors.joining("|"));
  }
}
