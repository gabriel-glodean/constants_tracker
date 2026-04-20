package org.glodean.constants.extractor.bytecode.handlers.impl;

import static java.lang.constant.ConstantDescs.CD_int;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.OperatorInstruction;
import java.util.EnumSet;
import org.glodean.constants.extractor.bytecode.handlers.InstructionHandler;
import org.glodean.constants.extractor.bytecode.types.ConstantPropagatingEntity;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.PrimitiveValue;
import org.glodean.constants.extractor.bytecode.types.State;

/**
 * Handles operator / arithmetic instructions. Performs constant propagation for binary operations
 * when possible and models arraylength.
 */
final class OperatorHandler implements InstructionHandler<OperatorInstruction> {
  private static final EnumSet<Opcode> SKIPPED_OPCODES =
      EnumSet.of(
          Opcode.INEG,
          Opcode.LNEG,
          Opcode.FNEG,
          Opcode.DNEG,
          Opcode.ISHL,
          Opcode.LSHL,
          Opcode.ISHR,
          Opcode.LSHR,
          Opcode.IUSHR,
          Opcode.LUSHR,
          Opcode.IAND,
          Opcode.LAND,
          Opcode.IOR,
          Opcode.LOR,
          Opcode.IXOR,
          Opcode.LXOR);

  private static final EnumSet<Opcode> COMPARE_OPCODES =
      EnumSet.of(Opcode.INEG, Opcode.LCMP, Opcode.FCMPG, Opcode.FCMPL, Opcode.DCMPG, Opcode.DCMPL);


  /**
   * {@inheritDoc}
   *
   * <p>Handles four categories:
   * <ul>
   *   <li>Unary/shift/bit opcodes ({@link #SKIPPED_OPCODES}) — no stack change (nop).</li>
   *   <li>Compare opcodes ({@link #COMPARE_OPCODES}) — pops two, pushes int result.</li>
   *   <li>{@code arraylength} — pops the array reference, pushes int length.</li>
   *   <li>Binary arithmetic — pops two operands, performs constant propagation if both
   *       are {@link ConstantPropagatingEntity}s, otherwise pushes a typed primitive.</li>
   * </ul>
   */
  @Override
  public void handle(OperatorInstruction oi, State state, String tag) {
    var opcode = oi.opcode();
    if (SKIPPED_OPCODES.contains(opcode)) {
      return;
    }
    if (COMPARE_OPCODES.contains(opcode)) {
      state.stack.removeLast();
      state.stack.removeLast();
      state.stack.addLast(PointsToSet.of(new PrimitiveValue(CD_int, tag)));
      return;
    }

    if (opcode == Opcode.ARRAYLENGTH) {
      state.stack.removeLast();
      state.stack.addLast(PointsToSet.of(new PrimitiveValue(CD_int, tag)));
      return;
    }

    var pointsToSet = new PointsToSet();
    var right = state.stack.removeLast();
    var left = state.stack.removeLast();
    for (var first : right) {
      for (var second : left) {
        if (first instanceof ConstantPropagatingEntity cpe1
            && second instanceof ConstantPropagatingEntity cpe2) {
          pointsToSet.add(cpe1.propagate(cpe2));
        }
      }
    }
    if (pointsToSet.isEmpty()) {
      pointsToSet.add(new PrimitiveValue(CD_int, tag));
    }
    state.stack.addLast(pointsToSet);
  }
}
