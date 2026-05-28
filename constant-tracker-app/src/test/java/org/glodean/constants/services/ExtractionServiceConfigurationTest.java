package org.glodean.constants.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import org.glodean.constants.extractor.ModelExtractorSupplierRepository;
import org.glodean.constants.extractor.bytecode.AnalysisMerger;
import org.glodean.constants.extractor.bytecode.ConstantUsageInterpreterRegistry;
import org.glodean.constants.extractor.bytecode.InternalStringConcatPatternSplitter;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Scheduler;

/**
 * Unit tests for ExtractionServiceConfiguration bean factory methods.
 */
class ExtractionServiceConfigurationTest {

  private final ExtractionServiceConfiguration config = new ExtractionServiceConfiguration();

  @Test
  void bytecodeAnalysisExecutor_returnsNonNullFixedPool() {
    ExecutorService executor = config.bytecodeAnalysisExecutor();
    assertThat(executor).isNotNull();
    executor.shutdown();
  }

  @Test
  void blockingIoScheduler_returnsNonNullScheduler() {
    Scheduler scheduler = config.blockingIoScheduler();
    assertThat(scheduler).isNotNull();
    config.shutdownBytecodeExecutor();
  }

  @Test
  void shutdownBytecodeExecutor_doesNotThrowWhenExecutorIsNull() {
    // If the @Bean method was never called, bytecodeAnalysisExecutor field is null.
    // shutdownBytecodeExecutor must handle this gracefully.
    ExtractionServiceConfiguration fresh = new ExtractionServiceConfiguration();
    fresh.shutdownBytecodeExecutor(); // must not throw
  }

  @Test
  void shutdownBytecodeExecutor_shutsDownExecutorWhenPresent() {
    ExecutorService executor = config.bytecodeAnalysisExecutor();
    assertThat(executor.isShutdown()).isFalse();
    config.shutdownBytecodeExecutor();
    assertThat(executor.isShutdown()).isTrue();
  }

  @Test
  void stringConcatPatternSplitter_returnsInternalImpl() {
    assertThat(config.stringConcatPatternSplitter())
        .isInstanceOf(InternalStringConcatPatternSplitter.class);
  }

  @Test
  void interpreterRegistry_returnsNonNullRegistry() {
    ConstantUsageInterpreterRegistry registry = config.interpreterRegistry();
    assertThat(registry).isNotNull();
  }

  @Test
  void analysisMerger_returnsNonNull() {
    var splitter = config.stringConcatPatternSplitter();
    var registry = config.interpreterRegistry();
    AnalysisMerger merger = config.analysisMerger(splitter, registry);
    assertThat(merger).isNotNull();
  }

  @Test
  void modelExtractorSupplierRepository_registersYamlAndPropertiesExtractors() {
    var splitter = config.stringConcatPatternSplitter();
    var registry = config.interpreterRegistry();
    AnalysisMerger merger = config.analysisMerger(splitter, registry);
    ModelExtractorSupplierRepository repo = config.modelExtractorSupplierRepository(merger);

    assertThat(repo).isNotNull();
    // .yml and .yaml files should resolve (factory stores bytes lazily — no parse on construction)
    assertThat(repo.resolve("app.yml", new byte[0])).isPresent();
    assertThat(repo.resolve("config.yaml", new byte[0])).isPresent();
    // .properties files should resolve
    assertThat(repo.resolve("app.properties", new byte[0])).isPresent();
    // unknown extensions should not resolve
    assertThat(repo.resolve("README.txt", new byte[0])).isEmpty();
    assertThat(repo.resolve("Foo.java", new byte[0])).isEmpty();
  }
}
