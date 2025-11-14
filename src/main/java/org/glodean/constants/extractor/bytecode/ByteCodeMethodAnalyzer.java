package org.glodean.constants.extractor.bytecode;

import static org.glodean.constants.extractor.bytecode.handlers.InstructionHandlerProvider.PROVIDER;

import com.google.common.collect.Streams;
import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.*;
import java.lang.reflect.AccessFlag;
import java.util.*;
import java.util.stream.Collectors;
import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity;
import org.glodean.constants.extractor.bytecode.types.State;

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

  ByteCodeMethodAnalyzer(ClassModel cm, MethodModel mm) {
    this.cm = cm;
    this.methodModel = mm;
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
      return;
    }
    this.code = codeModel.elementList();
    for (int i = 0; i < code.size(); i++) {
      in.add(null);
      out.add(null);
      successors.add(new ArrayList<>());
    }
    buildSuccessors();
  }

  private void buildSuccessors() {
    Streams.mapWithIndex(code.stream(), AbstractMap.SimpleImmutableEntry::new)
        .filter(Objects::nonNull)
        .filter(
            e -> {
              var element = e.getKey();
              return !(element instanceof ReturnInstruction || element instanceof ThrowInstruction);
            })
        .forEach(
            e -> {
              int i = Math.toIntExact(e.getValue());
              if (e.getKey() instanceof BranchInstruction bi) {
                if (bi.opcode() != Opcode.GOTO || bi.opcode() != Opcode.GOTO_W)
                  successors.get(i).add(i + 1);
                int nextInstruction = indexOfLabel(bi.target());
                successors.get(i).add(nextInstruction);
              }
              successors.get(i).add(i + 1);
            });
  }

  private int indexOfLabel(Label t) {
    for (int i = 0; i < code.size(); i++)
      if (code.get(i) instanceof LabelTarget lt && lt.label().equals(t)) return i;
    return -1;
  }

  public void run() {
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

  private State transfer(int i, State st) {
    CodeElement e = code.get(i);
    String tag = methodTag + "@" + i;
    switch (e) {
      case NewObjectInstruction ni ->
          PROVIDER.handlerFor(NewObjectInstruction.class).handle(ni, st, tag);
      case NewReferenceArrayInstruction nai ->
          PROVIDER.handlerFor(NewReferenceArrayInstruction.class).handle(nai, st, tag);
      case NewPrimitiveArrayInstruction npi ->
          PROVIDER.handlerFor(NewPrimitiveArrayInstruction.class).handle(npi, st, tag);
      case NewMultiArrayInstruction nmai ->
          PROVIDER.handlerFor(NewMultiArrayInstruction.class).handle(nmai, st, tag);
      case LoadInstruction li -> PROVIDER.handlerFor(LoadInstruction.class).handle(li, st, tag);
      case ConstantInstruction ci ->
          PROVIDER.handlerFor(ConstantInstruction.class).handle(ci, st, tag);
      case StoreInstruction si -> PROVIDER.handlerFor(StoreInstruction.class).handle(si, st, tag);
      case FieldInstruction fi -> PROVIDER.handlerFor(FieldInstruction.class).handle(fi, st, tag);
      case ArrayLoadInstruction ali ->
          PROVIDER.handlerFor(ArrayLoadInstruction.class).handle(ali, st, tag);
      case ArrayStoreInstruction asi ->
          PROVIDER.handlerFor(ArrayStoreInstruction.class).handle(asi, st, tag);
      case TypeCheckInstruction tc ->
          PROVIDER.handlerFor(TypeCheckInstruction.class).handle(tc, st, tag);
      case ReturnInstruction ri -> PROVIDER.handlerFor(ReturnInstruction.class).handle(ri, st, tag);
      case InvokeInstruction ii -> {
        PROVIDER.handlerFor(InvokeInstruction.class).handle(ii, st, tag);
        calls.add(
            methodTag
                + " -> "
                + ii.owner().asInternalName()
                + "."
                + ii.name().stringValue()
                + ii.type().stringValue());
      }
      case StackInstruction si -> PROVIDER.handlerFor(StackInstruction.class).handle(si, st, tag);
      case OperatorInstruction oi ->
          PROVIDER.handlerFor(OperatorInstruction.class).handle(oi, st, tag);
      case BranchInstruction bi -> PROVIDER.handlerFor(BranchInstruction.class).handle(bi, st, tag);
      case IncrementInstruction ii ->
          PROVIDER.handlerFor(IncrementInstruction.class).handle(ii, st, tag);
      default -> {}
    }

    return st;
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
