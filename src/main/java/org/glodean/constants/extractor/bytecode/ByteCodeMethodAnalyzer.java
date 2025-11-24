package org.glodean.constants.extractor.bytecode;

import static org.glodean.constants.extractor.bytecode.handlers.InstructionHandlerProvider.PROVIDER;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.*;
import java.lang.reflect.AccessFlag;
import java.util.*;
import java.util.stream.Collectors;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
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
  private final Map<Class<?>, InstructionHandler<Instruction>> handlerCache;

  ByteCodeMethodAnalyzer(ClassModel cm, MethodModel mm) {
    this.cm = cm;
    this.methodModel = mm;
    this.handlerCache = new HashMap<>();
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
    // delegate successor construction to SuccessorBuilder
    List<List<Integer>> built = SuccessorBuilder.build(code, methodModel);
    for (int i = 0; i < built.size() && i < successors.size(); i++) {
      successors.set(i, built.get(i));
    }
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
    dispatch(code.get(i), st, methodTag + "@" + i);
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void dispatch(CodeElement e, State st, String tag) {
    if (e instanceof Instruction ins) {

      Class<?> runtime = e.getClass();
      var handler =
          handlerCache.computeIfAbsent(
              runtime,
              k -> {
                // exact registration
                var h = PROVIDER.handlerFor((Class) k);
                if (h != null) return h;
                // try runtime interfaces
                Queue<Class<?>> toCheck = new ArrayDeque<>();
                for (Class<?> iface : k.getInterfaces()) {
                  h = PROVIDER.handlerFor((Class) iface);
                  if (h != null) return h;
                  toCheck.offer(iface);
                }
                // walk superclasses and their interfaces
                Class<?> s = k.getSuperclass();
                while (s != null) {
                  h = PROVIDER.handlerFor((Class) s);
                  if (h != null) return h;
                  for (Class<?> iface : s.getInterfaces()) {
                    h = PROVIDER.handlerFor((Class) iface);
                    if (h != null) return h;
                    toCheck.offer(iface);
                  }
                  var superClass = s.getSuperclass();
                  if (superClass != null) {
                    toCheck.offer(superClass);
                  }
                  s = toCheck.poll();
                }
                return null;
              });

      if (handler != null) {
        handler.handle(ins, st, tag);
        recordInvokeIfNeeded(ins);
      }
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
