package org.glodean.constants.extractor.bytecode;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.classfile.CodeElement;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.NopInstruction;
import java.util.List;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.samples.Greeter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link AnalysisMerger} correctly resolves source line numbers from
 * {@link LineNumber} pseudo-instructions embedded in the code element list.
 */
@DisplayName("AnalysisMerger – line number resolution")
class AnalysisMergerLineNumberTest {

  // ── buildLineNumbers() unit tests ─────────────────────────────────────────

  @Test
  @DisplayName("Returns -1 for every element when no LineNumber pseudo-instruction is present")
  void buildLineNumbers_noLineNumbers_allNegativeOne() {
    var code = List.<CodeElement>of(nop(), nop(), nop());

    int[] result = AnalysisMerger.buildLineNumbers(code);

    assertArrayEquals(new int[]{-1, -1, -1}, result);
  }

  @Test
  @DisplayName("Returns empty array for empty code list")
  void buildLineNumbers_emptyCode_emptyArray() {
    int[] result = AnalysisMerger.buildLineNumbers(List.of());

    assertEquals(0, result.length);
  }

  @Test
  @DisplayName("Applies the first LineNumber to all subsequent elements until the next one")
  void buildLineNumbers_singleLineNumber_appliedToSubsequentElements() {
    var code = List.<CodeElement>of(
        nop(),
        LineNumber.of(10),
        nop(),
        nop());

    int[] result = AnalysisMerger.buildLineNumbers(code);

    assertArrayEquals(new int[]{-1, 10, 10, 10}, result);
  }

  @Test
  @DisplayName("Switches active line when a second LineNumber pseudo-instruction is encountered")
  void buildLineNumbers_multipleLineNumbers_correctRanges() {
    var code = List.<CodeElement>of(
        LineNumber.of(5),
        nop(),
        LineNumber.of(9),
        nop(),
        nop());

    int[] result = AnalysisMerger.buildLineNumbers(code);

    assertArrayEquals(new int[]{5, 5, 9, 9, 9}, result);
  }

  @Test
  @DisplayName("LineNumber element itself carries the declared line, not -1")
  void buildLineNumbers_lineNumberElementItself_carriesDeclaredLine() {
    var code = List.<CodeElement>of(LineNumber.of(42));

    int[] result = AnalysisMerger.buildLineNumbers(code);

    assertArrayEquals(new int[]{42}, result);
  }

  // ── Integration: real .class file has non-null line numbers ──────────────

  @Test
  @DisplayName("Usages extracted from a compiled class carry non-null line numbers")
  void extractedUsages_haveNonNullLineNumbers() throws IOException {
    var model = TestUtils.convertClassToModel(Greeter.class);
    var merger = new AnalysisMerger(new InternalStringConcatPatternSplitter());
    var className = Utils.toJavaName(model.thisClass().asSymbol());
    var descriptor = new UnitDescriptor(BytecodeSourceKind.CLASS_FILE, className);

    UnitConstants result = new ClassModelExtractor(model, merger)
        .extract(descriptor)
        .iterator()
        .next();

    boolean anyLineNumber = result.constants().stream()
        .flatMap(c -> c.usages().stream())
        .map(ConstantUsage::location)
        .map(UsageLocation::lineNumber)
        .anyMatch(ln -> ln != null);

    assertTrue(anyLineNumber,
        "Expected at least one usage with a non-null line number from compiled class");
  }

  @Test
  @DisplayName("All usages extracted from a compiled class have non-null line numbers")
  void extractedUsages_allHaveNonNullLineNumbers() throws IOException {
    var model = TestUtils.convertClassToModel(Greeter.class);
    var merger = new AnalysisMerger(new InternalStringConcatPatternSplitter());
    var className = Utils.toJavaName(model.thisClass().asSymbol());
    var descriptor = new UnitDescriptor(BytecodeSourceKind.CLASS_FILE, className);

    UnitConstants result = new ClassModelExtractor(model, merger)
        .extract(descriptor)
        .iterator()
        .next();

    result.constants().stream()
        .flatMap(c -> c.usages().stream())
        .map(ConstantUsage::location)
        .forEach(loc ->
            assertNotNull(loc.lineNumber(),
                "UsageLocation in " + loc.className() + "#" + loc.methodName()
                    + "@" + loc.bytecodeOffset() + " should have a line number"));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static CodeElement nop() {
    return NopInstruction.of();
  }
}
