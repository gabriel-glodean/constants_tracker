package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import org.glodean.constants.extractor.bytecode.types.*;

final class FieldHandler implements InstructionHandler<FieldInstruction> {
  @Override
  public void handle(FieldInstruction fi, State state, String tag) {
    String owner = fi.owner().asInternalName();
    String name = fi.name().stringValue();
    String desc = fi.typeSymbol().descriptorString();
    Opcode m = fi.opcode();
    if (m == Opcode.GETSTATIC) {
      PointsToSet res =
          state.statics.getOrDefault(
              new StaticFieldKey(owner, name, desc),
              PointsToSet.of(StackAndParameterEntity.convert(fi.typeSymbol(), tag)));
      state.stack.addLast(res.copy());
    } else if (m == Opcode.PUTSTATIC) {
      PointsToSet val = state.stack.removeLast();
      state
          .statics
          .computeIfAbsent(new StaticFieldKey(owner, name, desc), _ -> new PointsToSet())
          .addAllFrom(val);
    } else if (m == Opcode.GETFIELD) {
      PointsToSet receiver = state.stack.removeLast();
      PointsToSet res = new PointsToSet();
      for (StackAndParameterEntity o : receiver) {
        var k = new FieldKey(o, owner, name, desc);
        var pts = state.heap.get(k);
        if (pts != null) {
          res.addAll(pts);
        } else {
          res.add(StackAndParameterEntity.convert(fi.typeSymbol(), tag));
        }
        ;
      }
      state.stack.addLast(res);
    } else if (m == Opcode.PUTFIELD) {
      PointsToSet val = state.stack.removeLast();
      PointsToSet receiver = state.stack.removeLast();
      if (receiver != null)
        for (StackAndParameterEntity o : receiver) {
          var k = new FieldKey(o, owner, name, desc);
          state.heap.computeIfAbsent(k, _ -> new PointsToSet()).addAllFrom(val);
        }
    }
  }
}
