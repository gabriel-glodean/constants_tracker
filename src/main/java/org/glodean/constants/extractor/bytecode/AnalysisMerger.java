package org.glodean.constants.extractor.bytecode;

import static java.lang.classfile.Opcode.*;
import static org.glodean.constants.model.ClassConstant.UsageType.*;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import java.lang.classfile.CodeElement;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.util.List;
import org.glodean.constants.extractor.bytecode.types.Constant;
import org.glodean.constants.extractor.bytecode.types.ConstantPropagation;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;
import org.glodean.constants.model.ClassConstant;

enum AnalysisMerger {
  MERGER;

  public Multimap<Object, ClassConstant.UsageType> merge(
      List<CodeElement> code, final List<State> in) {
    Multimap<Object, ClassConstant.UsageType> map = HashMultimap.create();
    Streams.forEachPair(code.stream(), in.stream(), (instr, state) -> handle(instr, state, map));
    return map;
  }

  private static void handle(
      CodeElement instr, State state, Multimap<Object, ClassConstant.UsageType> map) {
    if (instr == null) {
      return;
    }
    switch (instr) {
      case FieldInstruction fi when fi.opcode() == PUTFIELD ->
          handle(state.stack.getLast(), map, FIELD_STORE);
      case FieldInstruction fi when fi.opcode() == PUTSTATIC ->
          handle(state.stack.getLast(), map, STATIC_FIELD_STORE);
      case InvokeInstruction ii -> {
        int index = 1;
        for (; index <= ii.typeSymbol().parameterCount(); index++) {
          handle(state.stack.get(state.stack.size() - index), map, METHOD_INVOCATION_PARAMETER);
        }
        if (ii.opcode() != INVOKESTATIC) {
          handle(state.stack.get(state.stack.size() - index), map, METHOD_INVOCATION_TARGET);
        }
      }
      case IncrementInstruction ii ->
          handle(state.locals.get(ii.slot()), map, PROPAGATION_IN_ARITHMETIC_OPERATIONS);
      case OperatorInstruction oi when oi.opcode() != ARRAYLENGTH -> {
        handle(state.stack.getLast(), map, PROPAGATION_IN_ARITHMETIC_OPERATIONS);
        handle(state.stack.get(state.stack.size() - 2), map, PROPAGATION_IN_ARITHMETIC_OPERATIONS);
      }

      default -> {}
    }
  }

  private static void handle(
      PointsToSet stackAndParameterEntities,
      Multimap<Object, ClassConstant.UsageType> map,
      ClassConstant.UsageType usageType) {
    for (var entity : stackAndParameterEntities) {
      if (entity instanceof Constant<?> constantEntity) {
        map.put(constantEntity.value(), usageType);
      }
      if (entity instanceof ConstantPropagation(java.util.Set<Number> values)) {
        values.forEach(v -> map.put(v, usageType));
      }
    }
  }
}
