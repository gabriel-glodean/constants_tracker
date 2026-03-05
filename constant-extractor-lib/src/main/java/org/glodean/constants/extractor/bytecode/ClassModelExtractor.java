package org.glodean.constants.extractor.bytecode;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstants;

/**
 * Extracts {@link ClassConstants} from a {@link java.lang.classfile.ClassModel} by analyzing each
 * method's bytecode and merging discovered constants.
 *
 * <p>This extractor performs per-method bytecode analysis using {@link ByteCodeMethodAnalyzer}
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

  @Override
  public Collection<ClassConstants> extract() throws ExtractionException {
    Multimap<Object, ClassConstant.UsageType> joinedMap = HashMultimap.create();
    for (MethodModel mm : model.methods()) {
      if (mm.elementStream().noneMatch(e -> e instanceof CodeModel)) {
        continue;
      }
      var analysis = new ByteCodeMethodAnalyzer(model, mm);
      analysis.run();
      joinedMap.putAll(merger.merge(analysis.code, analysis.in));
    }

    // Convert UsageType multimap to Set<ConstantUsage> for new model
    Set<ClassConstant> constants = joinedMap.asMap().entrySet().stream()
        .map(entry -> {
          Set<ClassConstant.ConstantUsage> usages = entry.getValue().stream()
              .map(usageType -> new ClassConstant.ConstantUsage(
                  usageType,
                  ClassConstant.CoreSemanticType.UNKNOWN, // TODO: Implement semantic inference
                  new ClassConstant.UsageLocation(
                      model.thisClass().asInternalName(),
                      "<multiple>", // TODO: Track per-method location
                      "()V",
                      0,
                      null
                  ),
                  0.5 // TODO: Implement confidence scoring
              ))
              .collect(Collectors.toSet());
          return new ClassConstant(entry.getKey(), usages);
        })
        .collect(Collectors.toSet());

    return Set.of(new ClassConstants(model.thisClass().asInternalName(), constants));
  }
}
