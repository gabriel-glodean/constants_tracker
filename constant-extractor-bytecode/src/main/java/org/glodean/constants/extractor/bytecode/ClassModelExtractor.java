package org.glodean.constants.extractor.bytecode;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;


import static org.glodean.constants.extractor.bytecode.Utils.toJavaDescriptor;
import static org.glodean.constants.extractor.bytecode.Utils.toJavaName;

/**
 * Extracts {@link UnitConstants} from a {@link java.lang.classfile.ClassModel} by analyzing each
 * method's bytecode and merging discovered constants.
 *
 * <p>This extractor performs per-method bytecode analysis using {@link BytecodeMethodAnalyzer}
 * to compute abstract states (constant propagation, points-to information), then uses
 * {@link AnalysisMerger} to extract constant usage patterns from those states.
 *
 * <p>The analysis is conservative and inter-procedural within a single class:
 * <ul>
 *   <li>Tracks constants through local variables and the operand stack</li>
 *   <li>Identifies usage contexts (method parameters, field stores, arithmetic)</li>
 *   <li>Handles control flow (branches, loops, exception handlers)</li>
 *   <li>Does not perform cross-class analysis (each class analyzed independently)</li>
 * </ul>
 *
 * @param model the Java class model to analyze (from Class-File API)
 * @param merger the merger that converts bytecode states to constant usage mappings
 */
public record ClassModelExtractor(ClassModel model, AnalysisMerger merger)
    implements ModelExtractor {

  /**
   * Returns a supplier that parses raw class-file bytes and creates a
   * {@link ClassModelExtractor} for the given {@link AnalysisMerger}.
   *
   * <p>Register this in a {@link org.glodean.constants.extractor.ModelExtractorSupplierRepository}
   * to avoid embedding the construction logic in configuration classes:
   * <pre>{@code
   * .register(name -> name.endsWith(".class"), BytecodeSourceKind.CLASS_FILE, ClassModelExtractor.supplier(merger))
   * }</pre>
   *
   * @param merger the shared merger instance to capture in the supplier
   * @return a {@code Function<byte[], ModelExtractor>} suitable for repository registration
   */
  public static Function<byte[], ModelExtractor> supplier(AnalysisMerger merger) {
    return bytes -> new ClassModelExtractor(ClassFile.of().parse(bytes), merger);
  }


  @Override
  public Collection<UnitConstants> extract(UnitDescriptor source) throws ExtractionException {
    Multimap<Object, ConstantUsage> joinedMap = HashMultimap.create();
    String javaClassName = toJavaName(model.thisClass().asSymbol());

    // Enrich the descriptor with the actual class name derived from the bytecode
    var enriched = new UnitDescriptor(
        source.sourceKind(), javaClassName, source.sizeBytes(), source.contentHash());

    // Extract constants from annotations (class, field, method, parameter level)
    var annotationExtractor = new AnnotationConstantExtractor(merger.usageInterpreterRegistry());
    joinedMap.putAll(annotationExtractor.extract(model, javaClassName));

    // Extract compile-time constants from ConstantValue attributes on static final fields
    var staticFieldExtractor = new StaticFinalFieldConstantExtractor(merger.usageInterpreterRegistry());
    joinedMap.putAll(staticFieldExtractor.extract(model, javaClassName));

    for (MethodModel mm : model.methods()) {
      if (mm.elementStream().noneMatch(e -> e instanceof CodeModel)) {
        continue;
      }
      var analysis = new BytecodeMethodAnalyzer(model, mm);
      analysis.run();
      joinedMap.putAll(merger.merge(
          javaClassName,
          mm.methodName().stringValue(),
          toJavaDescriptor(mm.methodType().stringValue()),
          analysis.code,
          analysis.in));
    }

    Set<UnitConstant> constants = joinedMap.asMap().entrySet().stream()
        .map(entry -> new UnitConstant(entry.getKey(), new HashSet<>(entry.getValue())))
        .collect(Collectors.toSet());

    return Set.of(new UnitConstants(enriched, constants));
  }
}
