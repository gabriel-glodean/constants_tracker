package org.glodean.constants.extractor.bytecode;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
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
}
