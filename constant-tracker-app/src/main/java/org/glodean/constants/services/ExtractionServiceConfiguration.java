package org.glodean.constants.services;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import org.glodean.constants.extractor.ModelExtractorSupplierRepository;
import org.glodean.constants.extractor.bytecode.AnalysisMerger;
import org.glodean.constants.extractor.bytecode.BytecodeSourceKind;
import org.glodean.constants.extractor.bytecode.ClassModelExtractor;
import org.glodean.constants.extractor.bytecode.ConstantUsageInterpreterRegistry;
import org.glodean.constants.extractor.configfile.ConfigFileSourceKind;
import org.glodean.constants.extractor.configfile.PropertiesConstantsExtractor;
import org.glodean.constants.extractor.configfile.YamlConstantsExtractor;
import org.glodean.constants.extractor.bytecode.InternalStringConcatPatternSplitter;
import org.glodean.constants.extractor.bytecode.StringConcatPatternSplitter;
import org.glodean.constants.extractor.bytecode.interpreters.AnnotationConstantUsageInterpreter;
import org.glodean.constants.extractor.bytecode.interpreters.ErrorMessageConstantUsageInterpreter;
import org.glodean.constants.extractor.bytecode.interpreters.FilePathConstantUsageInterpreter;
import org.glodean.constants.extractor.bytecode.interpreters.LoggingConstantUsageInterpreter;
import org.glodean.constants.extractor.bytecode.interpreters.SqlConstantUsageInterpreter;
import org.glodean.constants.extractor.bytecode.interpreters.UrlResourceConstantUsageInterpreter;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PreDestroy;

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

  private static final Logger logger = LogManager.getLogger(ExtractionServiceConfiguration.class);

  private ExecutorService bytecodeAnalysisExecutor;
  private ExecutorService blockingIoExecutor;

  /**
   * Shared fixed-thread-pool used by all bytecode extractions.
   * Sized to {@link Runtime#availableProcessors()} — CPU-bound workload.
   * Shut down on application context close via {@link #shutdownBytecodeExecutor()}.
   */
  @Bean
  ExecutorService bytecodeAnalysisExecutor() {
    int threads = Runtime.getRuntime().availableProcessors();
    logger.atInfo().log("Creating shared bytecode analysis executor with {} threads", threads);
    this.bytecodeAnalysisExecutor = Executors.newFixedThreadPool(threads);
    return this.bytecodeAnalysisExecutor;
  }

  /**
   * Reactor {@link Scheduler} for blocking I/O offloading, backed by virtual threads.
   *
   * <p>Used wherever blocking I/O (ZIP reading, class extraction) must be moved off the
   * event loop via {@code subscribeOn}. Virtual threads are cheap to create and park
   * without holding OS threads, making them a better fit than {@code boundedElastic} for
   * this workload.
   */
  @Bean
  Scheduler blockingIoScheduler() {
    logger.atInfo().log("Creating virtual-thread-backed blocking I/O scheduler");
    this.blockingIoExecutor = Executors.newVirtualThreadPerTaskExecutor();
    return Schedulers.fromExecutorService(blockingIoExecutor, "vt-io");
  }

  @PreDestroy
  void shutdownBytecodeExecutor() {
    if (bytecodeAnalysisExecutor != null) {
      logger.atInfo().log("Shutting down bytecode analysis executor");
      bytecodeAnalysisExecutor.close();
    }
    if (blockingIoExecutor != null) {
      logger.atInfo().log("Shutting down blocking I/O executor");
      blockingIoExecutor.close();
    }
  }

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
   * Unified {@link ModelExtractorSupplierRepository} covering all supported source kinds:
   * <ul>
   *   <li>{@link BytecodeSourceKind#CLASS_FILE} — via {@link ClassModelExtractor#supplier}</li>
   *   <li>{@link ConfigFileSourceKind#YAML} — via {@link YamlConstantsExtractor}</li>
   *   <li>{@link ConfigFileSourceKind#PROPERTIES} — via {@link PropertiesConstantsExtractor}</li>
   * </ul>
   *
   * <p>To add a new source kind, add a {@code .register(...)} call here.
   * No changes to controllers or services are required.
   *
   * @param merger the shared {@link AnalysisMerger} bean
   * @return a single immutable repository used by all extraction entry points
   */
  @Bean
  ModelExtractorSupplierRepository modelExtractorSupplierRepository(AnalysisMerger merger) {
    return ModelExtractorSupplierRepository.builder()
        .register(
            name -> name.endsWith(".class"),
            BytecodeSourceKind.CLASS_FILE,
            ClassModelExtractor.supplier(merger))
        .register(
            n -> n.endsWith(".yml") || n.endsWith(".yaml"),
            ConfigFileSourceKind.YAML,
            YamlConstantsExtractor::new)
        .register(
            n -> n.endsWith(".properties"),
            ConfigFileSourceKind.PROPERTIES,
            PropertiesConstantsExtractor::new)
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
