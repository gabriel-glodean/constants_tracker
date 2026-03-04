package org.glodean.constants.extractor.bytecode.handlers.impl;

import static org.apache.commons.lang3.function.Consumers.nop;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.StackInstruction;
import java.util.Collection;
import java.util.EnumMap;
import java.util.function.Consumer;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.SizeType;
import org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity;
import org.glodean.constants.extractor.bytecode.types.State;

/** Handler for stack manipulation instructions (e.g., DUP). Models the simple DUP operation. */
final class StackHandler implements InstructionHandler<StackInstruction> {

  private static final EnumMap<SizeType, EnumMap<Opcode, Consumer<State>>> HANDLERS;

  static {
    HANDLERS = new EnumMap<>(SizeType.class);
    var size1Handlers = new EnumMap<Opcode, Consumer<State>>(Opcode.class);
    var size2Handlers = new EnumMap<Opcode, Consumer<State>>(Opcode.class);
    HANDLERS.put(SizeType.SINGLE_CELL, size1Handlers);
    HANDLERS.put(SizeType.DOUBLE_CELL, size2Handlers);

    size1Handlers.put(
        Opcode.DUP,
        state -> {
          requireSize(state, 1, Opcode.DUP);
          state.stack.addLast(state.stack.getLast().copy());
        });

    size1Handlers.put(
        Opcode.POP,
        state -> {
          requireSize(state, 1, Opcode.POP);
          state.stack.removeLast();
        });

    size1Handlers.put(
        Opcode.POP2,
        state -> {
          requireSize(state, 2, Opcode.POP2);
          state.stack.removeLast();
          state.stack.removeLast();
        });

    size1Handlers.put(
        Opcode.SWAP,
        state -> {
          requireSize(state, 2, Opcode.SWAP);
          var v1 = state.stack.removeLast();
          var v2 = state.stack.removeLast();
          state.stack.addLast(v1.copy());
          state.stack.addLast(v2.copy());
        });

    size1Handlers.put(
        Opcode.DUP_X1,
        state -> {
          requireSize(state, 2, Opcode.DUP_X1);
          var v1 = state.stack.removeLast();
          var v2 = state.stack.removeLast();
          state.stack.addLast(v1.copy());
          state.stack.addLast(v2.copy());
          state.stack.addLast(v1.copy());
        });

    size1Handlers.put(
        Opcode.DUP_X2,
        state -> {
          requireSize(state, 3, Opcode.DUP_X2);
          var v1 = state.stack.removeLast();
          var v2 = state.stack.removeLast();
          var v3 = state.stack.removeLast();
          state.stack.addLast(v1.copy());
          state.stack.addLast(v3.copy());
          state.stack.addLast(v2.copy());
          state.stack.addLast(v1.copy());
        });

    size1Handlers.put(
        Opcode.DUP2,
        state -> {
          requireSize(state, 2, Opcode.DUP2);
          var v1 = state.stack.removeLast();
          var v2 = state.stack.removeLast();
          state.stack.addLast(v2.copy());
          state.stack.addLast(v1.copy());
          state.stack.addLast(v2.copy());
          state.stack.addLast(v1.copy());
        });

    size1Handlers.put(
        Opcode.DUP2_X1,
        state -> {
          requireSize(state, 3, Opcode.DUP2_X1);
          var v1 = state.stack.removeLast();
          var v2 = state.stack.removeLast();
          var v3 = state.stack.removeLast();
          state.stack.addLast(v2.copy());
          state.stack.addLast(v1.copy());
          state.stack.addLast(v3.copy());
          state.stack.addLast(v2.copy());
          state.stack.addLast(v1.copy());
        });

    size1Handlers.put(
        Opcode.DUP2_X2,
        state -> {
          requireSize(state, 4, Opcode.DUP2_X2);
          var v1 = state.stack.removeLast();
          var v2 = state.stack.removeLast();
          var v3 = state.stack.removeLast();

          if (getSizeType(v3) == SizeType.DOUBLE_CELL) {
            state.stack.addLast(v2.copy());
            state.stack.addLast(v1.copy());
            state.stack.addLast(v3.copy());
            state.stack.addLast(v2.copy());
            state.stack.addLast(v1.copy());
            return;
          }

          var v4 = state.stack.removeLast();
          state.stack.addLast(v2.copy());
          state.stack.addLast(v1.copy());
          state.stack.addLast(v4.copy());
          state.stack.addLast(v3.copy());
          state.stack.addLast(v2.copy());
          state.stack.addLast(v1.copy());
        });

    size2Handlers.put(
        Opcode.POP,
        state -> {
          requireSize(state, 1, Opcode.POP);
          state.stack.removeLast();
        });

    size2Handlers.put(
        Opcode.POP2,
        state -> {
          // when top is category2, POP2 removes the single category2 value
          requireSize(state, 1, Opcode.POP2);
          state.stack.removeLast();
        });

    size2Handlers.put(
        Opcode.DUP2,
        state -> {
          // duplicate the single category2 value
          requireSize(state, 1, Opcode.DUP2);
          var v1 = state.stack.getLast();
          state.stack.addLast(v1.copy());
        });

    size2Handlers.put(
        Opcode.DUP2_X1,
        state -> {
          // form where top is category2 and next is category1:
          // ... , v2, v1 -> ... , v1, v2, v1
          requireSize(state, 2, Opcode.DUP2_X1);
          var v1 = state.stack.removeLast();
          var v2 = state.stack.removeLast();
          state.stack.addLast(v1.copy());
          state.stack.addLast(v2.copy());
          state.stack.addLast(v1.copy());
        });

    size2Handlers.put(
        Opcode.DUP2_X2,
        state -> {
          // form where top is category2 and next is category2 (or other valid form):
          // ... , v2, v1 -> ... , v1, v2, v1
          requireSize(state, 2, Opcode.DUP2_X2);
          var v1 = state.stack.removeLast();
          var v2 = state.stack.removeLast();
          if (getSizeType(v2) == SizeType.DOUBLE_CELL) {
            state.stack.addLast(v1.copy());
            state.stack.addLast(v2.copy());
            state.stack.addLast(v1.copy());
            return;
          }
          var v3 = state.stack.removeLast();
          state.stack.addLast(v1.copy());
          state.stack.addLast(v3.copy());
          state.stack.addLast(v2.copy());
          state.stack.addLast(v1.copy());
        });

    // mark single-cell-only ops as invalid when top is category2
    size2Handlers.put(
        Opcode.DUP,
        _ -> {
          throw new IllegalArgumentException("Opcode.DUP not valid when top of stack is category2");
        });
    size2Handlers.put(
        Opcode.SWAP,
        _ -> {
          throw new IllegalArgumentException(
              "Opcode.SWAP not valid when top of stack is category2");
        });
    size2Handlers.put(
        Opcode.DUP_X1,
        _ -> {
          throw new IllegalArgumentException(
              "Opcode.DUP_X1 not valid when top of stack is category2");
        });
    size2Handlers.put(
        Opcode.DUP_X2,
        _ -> {
          throw new IllegalArgumentException(
              "Opcode.DUP_X2 not valid when top of stack is category2");
        });
  }

  static SizeType getSizeType(Collection<StackAndParameterEntity> entities) {
    var sizes = entities.stream().map(StackAndParameterEntity::size).distinct().toList();
    return sizes.size() == 1 ? sizes.getFirst() : SizeType.SINGLE_CELL;
  }

  @Override
  public void handle(StackInstruction si, State state, String tag) {
    var handler = HANDLERS.get(getSizeType(state.stack.getLast())).getOrDefault(si.opcode(), nop());
    handler.accept(state);
  }

  // helper that enforces stack size and fails fast when the stack is too small
  private static void requireSize(State state, int required, Opcode opcode) {
    int sz = state.stack.size();
    if (sz < required) {
      throw new IllegalArgumentException(
          opcode + " requires at least " + required + " stack element(s), but found " + sz);
    }
  }
}
