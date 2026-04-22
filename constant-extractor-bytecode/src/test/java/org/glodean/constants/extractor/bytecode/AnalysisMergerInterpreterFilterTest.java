package org.glodean.constants.extractor.bytecode;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Collection;
import java.util.stream.Collectors;
import org.glodean.constants.interpreter.ConstantUsageInterpreter;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link AnalysisMerger} suppresses zero-confidence fallback usages
 * when at least one interpreter produces a meaningful (confidence &gt; 0) classification
 * for the same constant usage.
 *
 * <p>Uses the classfile API to generate synthetic bytecode so the tests don't depend
 * on external sample classes.
 */
@DisplayName("AnalysisMerger – interpreter filter (no duplicate UNKNOWN usages)")
class AnalysisMergerInterpreterFilterTest {

  /** Always returns UNKNOWN/0.0 — simulates a non-matching interpreter. */
  private static final ConstantUsageInterpreter UNKNOWN_INTERP = new ConstantUsageInterpreter() {
    @Override public ConstantUsage interpret(UsageLocation location, InterpretationContext ctx) {
      return new ConstantUsage(UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.UNKNOWN, location, 0.0);
    }
    @Override public boolean canInterpret(UsageType type) { return type == UsageType.METHOD_INVOCATION_PARAMETER; }
  };

  /** Always returns SQL_FRAGMENT/0.95 — simulates a matching interpreter. */
  private static final ConstantUsageInterpreter SQL_INTERP = new ConstantUsageInterpreter() {
    @Override public ConstantUsage interpret(UsageLocation location, InterpretationContext ctx) {
      return new ConstantUsage(UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.SQL_FRAGMENT, location, 0.95);
    }
    @Override public boolean canInterpret(UsageType type) { return type == UsageType.METHOD_INVOCATION_PARAMETER; }
  };

  /** Always returns LOG_MESSAGE/0.8 — simulates a second matching interpreter. */
  private static final ConstantUsageInterpreter LOG_INTERP = new ConstantUsageInterpreter() {
    @Override public ConstantUsage interpret(UsageLocation location, InterpretationContext ctx) {
      return new ConstantUsage(UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.LOG_MESSAGE, location, 0.8);
    }
    @Override public boolean canInterpret(UsageType type) { return type == UsageType.METHOD_INVOCATION_PARAMETER; }
  };

  // ── Tests ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("When no interpreter matches (all confidence=0), keeps the single UNKNOWN fallback")
  void noMatchingInterpreter_keepsUnknownFallback() throws Exception {
    var registry = ConstantUsageInterpreterRegistry.builder()
        .register(UsageType.METHOD_INVOCATION_PARAMETER, UNKNOWN_INTERP)
        .register(UsageType.METHOD_INVOCATION_PARAMETER, UNKNOWN_INTERP)
        .build();

    Collection<ConstantUsage> usages = extractUsagesFor("SELECT 1", registry);

    // Both interpreters produce identical UNKNOWN/0.0 → deduplicated to 1
    assertEquals(1, usages.size(), "Expected 1 UNKNOWN fallback, got: " + usages);
    var usage = usages.iterator().next();
    assertEquals(CoreSemanticType.UNKNOWN, usage.semanticType());
    assertEquals(0.0, usage.confidence());
  }

  @Test
  @DisplayName("When one interpreter matches and others return UNKNOWN/0.0, only the matched result is kept")
  void matchingInterpreterPresent_suppressesUnknownFallbacks() throws Exception {
    var registry = ConstantUsageInterpreterRegistry.builder()
        .register(UsageType.METHOD_INVOCATION_PARAMETER, UNKNOWN_INTERP)
        .register(UsageType.METHOD_INVOCATION_PARAMETER, SQL_INTERP)
        .register(UsageType.METHOD_INVOCATION_PARAMETER, UNKNOWN_INTERP)
        .build();

    Collection<ConstantUsage> usages = extractUsagesFor("SELECT 1", registry);

    assertEquals(1, usages.size(), "Expected exactly 1 usage (the matched one), but got: " + usages);
    var usage = usages.iterator().next();
    assertEquals(CoreSemanticType.SQL_FRAGMENT, usage.semanticType());
    assertEquals(0.95, usage.confidence());
  }

  @Test
  @DisplayName("When multiple interpreters match with positive confidence, all matched results are kept")
  void multipleMatchingInterpreters_allKept() throws Exception {
    var registry = ConstantUsageInterpreterRegistry.builder()
        .register(UsageType.METHOD_INVOCATION_PARAMETER, SQL_INTERP)
        .register(UsageType.METHOD_INVOCATION_PARAMETER, LOG_INTERP)
        .register(UsageType.METHOD_INVOCATION_PARAMETER, UNKNOWN_INTERP)
        .build();

    Collection<ConstantUsage> usages = extractUsagesFor("SELECT 1", registry);

    assertEquals(2, usages.size(), "Expected 2 usages (SQL + LOG), got: " + usages);
    var semanticTypes = usages.stream()
        .map(ConstantUsage::semanticType)
        .collect(Collectors.toSet());
    assertTrue(semanticTypes.contains(CoreSemanticType.SQL_FRAGMENT));
    assertTrue(semanticTypes.contains(CoreSemanticType.LOG_MESSAGE));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Generates a synthetic class whose {@code test()} method loads the given string constant
   * and passes it to {@code java/sql/Statement.executeQuery(String)ResultSet}, then extracts
   * constants using the provided registry and returns the usages recorded for that string.
   */
  private static Collection<ConstantUsage> extractUsagesFor(
      String constantValue, ConstantUsageInterpreterRegistry registry) throws Exception {

    ClassDesc resultSetDesc = ClassDesc.ofInternalName("java/sql/ResultSet");
    ClassDesc statementDesc = ClassDesc.ofInternalName("java/sql/Statement");

    byte[] bytes = ClassFile.of().build(
        ClassDesc.of("synthetic.FilterTest"),
        cb -> cb.withMethod("test", MethodTypeDesc.of(CD_void, statementDesc), ACC_PUBLIC | ACC_STATIC,
            mb -> mb.withCode(xb -> {
              xb.aload(0);                        // load Statement
              xb.ldc(constantValue);              // load the string constant
              xb.invokevirtual(statementDesc, "executeQuery",
                  MethodTypeDesc.of(resultSetDesc, CD_String));
              xb.pop();
              xb.return_();
            })));

    ClassModel cm = ClassFile.of().parse(bytes);
    var merger = new AnalysisMerger(new InternalStringConcatPatternSplitter(), registry);
    UnitConstants result = new ClassModelExtractor(cm, merger)
        .extract(new UnitDescriptor(BytecodeSourceKind.CLASS_FILE, "synthetic.FilterTest"))
        .iterator()
        .next();

    return result.constants().stream()
        .filter(c -> constantValue.equals(c.value()))
        .findFirst()
        .map(UnitConstant::usages)
        .orElse(java.util.Set.of());
  }
}

