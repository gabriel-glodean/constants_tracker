package org.glodean.constants.services;

import org.glodean.constants.extractor.bytecode.AnalysisMerger;
import org.glodean.constants.extractor.bytecode.ConstantUsageInterpreterRegistry;
import org.glodean.constants.extractor.bytecode.InternalStringConcatPatternSplitter;
import org.glodean.constants.extractor.bytecode.StringConcatPatternSplitter;
import org.glodean.constants.extractor.bytecode.interpreters.AnnotationConstantUsageInterpreter;
import org.glodean.constants.extractor.bytecode.interpreters.ErrorMessageConstantUsageInterpreter;
import org.glodean.constants.extractor.bytecode.interpreters.FilePathConstantUsageInterpreter;
import org.glodean.constants.extractor.bytecode.interpreters.LoggingConstantUsageInterpreter;
import org.glodean.constants.extractor.bytecode.interpreters.SqlConstantUsageInterpreter;
import org.glodean.constants.extractor.bytecode.interpreters.UrlResourceConstantUsageInterpreter;
import org.glodean.constants.model.ClassConstant.UsageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring {@link Configuration} that wires the bytecode extraction infrastructure.
 *
 * <p>This configuration class produces the shared beans used by
 * {@link ConcreteExtractionService}:
 * <ul>
 *   <li>{@link StringConcatPatternSplitter} — splits JVM string-concat patterns into literals</li>
 *   <li>{@link ConstantUsageInterpreterRegistry} — maps usage types to semantic interpreters</li>
 *   <li>{@link AnalysisMerger} — combines per-instruction states into constant usages</li>
 * </ul>
 *
 * <p>To extend the semantic analysis, add further
 * {@link org.glodean.constants.interpreter.ConstantUsageInterpreter} registrations inside
 * {@link #interpreterRegistry()}.
 */
@Configuration
public class ExtractionServiceConfiguration {

  /**
   * Creates the {@link StringConcatPatternSplitter} that extracts literal parts from
   * {@code invokedynamic} string-concatenation patterns.
   *
   * @return an {@link InternalStringConcatPatternSplitter} using the JDK-internal separator
   */
  @Bean
  StringConcatPatternSplitter stringConcatPatternSplitter() {
    return new InternalStringConcatPatternSplitter();
  }

  /**
   * Creates the {@link ConstantUsageInterpreterRegistry} that maps structural usage types to
   * semantic interpreters.
   *
   * <p>Currently registers a {@link LoggingConstantUsageInterpreter} for
   * {@link UsageType#METHOD_INVOCATION_PARAMETER}. Add additional
   * {@code .register(...)} calls here to plug in new semantic classifiers.
   *
   * @return an immutable registry with all registered interpreters
   */
  @Bean
  ConstantUsageInterpreterRegistry interpreterRegistry() {
    return ConstantUsageInterpreterRegistry.builder()
        .register(UsageType.METHOD_INVOCATION_PARAMETER, new LoggingConstantUsageInterpreter())
        .register(UsageType.METHOD_INVOCATION_PARAMETER, new SqlConstantUsageInterpreter())
        .register(UsageType.METHOD_INVOCATION_PARAMETER, new ErrorMessageConstantUsageInterpreter())
        .register(UsageType.METHOD_INVOCATION_PARAMETER, new FilePathConstantUsageInterpreter())
        .register(UsageType.METHOD_INVOCATION_PARAMETER, new UrlResourceConstantUsageInterpreter())
        .register(UsageType.ANNOTATION_VALUE, new AnnotationConstantUsageInterpreter())
        .build();
  }

  /**
   * Creates the {@link AnalysisMerger} shared by all extractors.
   *
   * @param splitter  the string-concat pattern splitter bean
   * @param registry  the interpreter registry bean
   * @return a configured {@link AnalysisMerger} instance
   */
  @Bean
  AnalysisMerger analysisMerger(
      @Autowired StringConcatPatternSplitter splitter,
      @Autowired ConstantUsageInterpreterRegistry registry) {
    return new AnalysisMerger(splitter, registry);
  }
}
