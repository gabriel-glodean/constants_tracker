package org.glodean.constants.extractor.bytecode;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstants;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public record ClassModelExtractor(ClassModel model) implements ModelExtractor {

    @Override
    public Collection<ClassConstants> extract() throws ExtractionException {
        Multimap<Object, ClassConstant.UsageType> joinedMap = HashMultimap.create();
        for (MethodModel mm : model.methods()) {
            if (mm.elementStream().noneMatch(e -> e instanceof CodeModel)){
                continue;
            }
            var analysis = new ByteCodeMethodAnalyzer(model, mm);
            analysis.run();
            joinedMap.putAll(AnalysisMerger.MERGER.merge(analysis.code, analysis.in));
        }
        return Set.of(new ClassConstants(model.thisClass().asInternalName(), joinedMap.asMap().entrySet().stream()
                .map(e -> new ClassConstant(e.getKey(), e.getValue()))
                .collect(Collectors.toSet())));
    }
}
