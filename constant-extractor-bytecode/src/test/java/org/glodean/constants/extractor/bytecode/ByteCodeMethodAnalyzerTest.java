package org.glodean.constants.extractor.bytecode;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.util.List;
import org.glodean.constants.samples.Greeter;
import org.glodean.constants.samples.SimpleIfElse;
import org.junit.jupiter.api.Test;

class ByteCodeMethodAnalyzerTest {

  @Test
  void testBuildSuccessors_simpleIfElse() throws IOException {
    var cm = TestUtils.convertClassToModel(SimpleIfElse.class);
    var mm =
        cm.methods().stream()
            .filter(m -> m.methodName().stringValue().equals("numberIfElse"))
            .findFirst()
            .orElseThrow();
    var analyzer = new ByteCodeMethodAnalyzer(cm, mm);

    List<CodeElement> code = analyzer.code;

    // helper to find label target index
    java.util.function.BiFunction<Label, List<CodeElement>, Integer> findLabelIndex =
        (t, c) -> {
          for (int j = 0; j < c.size(); j++) {
            if (c.get(j) instanceof java.lang.classfile.instruction.LabelTarget lt
                && lt.label().equals(t)) return j;
          }
          return -1;
        };

    for (int i = 0; i < code.size(); i++) {
      var e = code.get(i);
      if (e instanceof BranchInstruction bi) {
        if (bi.opcode() == Opcode.GOTO || bi.opcode() == Opcode.GOTO_W) {
          // GOTO should only jump to target (no fall-through)
          assertEquals(
              1,
              analyzer.successors.get(i).size(),
              "GOTO should have a single successor (the target)");
          int targetIdx = findLabelIndex.apply(bi.target(), code);
          assertTrue(analyzer.successors.get(i).contains(targetIdx));
        } else {
          // other branch instructions should have a fall-through (i+1) and a target
          assertTrue(
              analyzer.successors.get(i).size() >= 2,
              "conditional branch should have at least two successors");
          assertTrue(
              analyzer.successors.get(i).contains(i + 1),
              "should include fall-through successor i+1");
          int targetIdx = findLabelIndex.apply(bi.target(), code);
          assertTrue(
              analyzer.successors.get(i).contains(targetIdx), "should include branch target index");
        }
      } else if (e instanceof ReturnInstruction || e instanceof ThrowInstruction) {
        // return/throw filtered out in buildSuccessors -> should have empty successors list
        assertTrue(analyzer.successors.get(i).isEmpty(), "return/throw should have no successors");
      } else {
        // Non-branch instructions should at least contain i+1 (may be out-of-bounds)
        assertFalse(
            analyzer.successors.get(i).isEmpty(),
            "non-branch should have successors entry (may point to i+1)");
      }
    }
  }

  @Test
  void testDispatch_recordsInvoke_forGreeter() throws IOException {
    var cm = TestUtils.convertClassToModel(Greeter.class);
    var mm =
        cm.methods().stream()
            .filter(m -> m.methodName().stringValue().equals("greet"))
            .findFirst()
            .orElseThrow();
    var analyzer = new ByteCodeMethodAnalyzer(cm, mm);
    analyzer.run();

    // There should be at least one recorded invoke (e.g. String.formatted or String.length)
    assertFalse(analyzer.calls.isEmpty(), "Expected at least one recorded invocation");
    boolean foundFormatted =
        analyzer.calls.stream()
            .anyMatch(s -> s.contains(".formatted") || s.contains("String.formatted"));
    boolean foundLength =
        analyzer.calls.stream().anyMatch(s -> s.contains(".length") || s.contains("String.length"));
    assertTrue(
        foundFormatted || foundLength,
        "Expected recorded calls to include String.formatted or String.length");
  }
}
