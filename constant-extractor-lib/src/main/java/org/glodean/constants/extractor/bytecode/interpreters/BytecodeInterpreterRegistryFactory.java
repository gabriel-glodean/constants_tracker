package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.extractor.bytecode.ConstantUsageInterpreterRegistry;
import org.glodean.constants.model.ClassConstant;

/**
 * Factory for constructing the standard {@link ConstantUsageInterpreterRegistry} used
 * inside the {@code constant-extractor-lib} module.
 *
 * <p>This factory wires the {@link LoggingConstantUsageInterpreter} for
 * {@link ClassConstant.UsageType#METHOD_INVOCATION_PARAMETER}. In the Spring Boot app,
 * the equivalent wiring is performed by
 * {@code org.glodean.constants.services.ExtractionServiceConfiguration}; this factory
 * serves library consumers that operate outside of Spring.
 */
public final class BytecodeInterpreterRegistryFactory {

  /** Not instantiable — use {@link #registry()} directly. */
  private BytecodeInterpreterRegistryFactory() {}

  /**
   * Builds and returns a {@link ConstantUsageInterpreterRegistry} with the default
   * set of semantic interpreters.
   *
   * @return an immutable registry with the {@link LoggingConstantUsageInterpreter}
   *         registered for {@link ClassConstant.UsageType#METHOD_INVOCATION_PARAMETER}
   */
  public static ConstantUsageInterpreterRegistry registry() {
    return ConstantUsageInterpreterRegistry.builder()
        .register(ClassConstant.UsageType.METHOD_INVOCATION_PARAMETER, new LoggingConstantUsageInterpreter())
        .register(ClassConstant.UsageType.METHOD_INVOCATION_PARAMETER, new SqlConstantUsageInterpreter())
        .register(ClassConstant.UsageType.METHOD_INVOCATION_PARAMETER, new ErrorMessageConstantUsageInterpreter())
        .register(ClassConstant.UsageType.METHOD_INVOCATION_PARAMETER, new FilePathConstantUsageInterpreter())
        .register(ClassConstant.UsageType.METHOD_INVOCATION_PARAMETER, new UrlResourceConstantUsageInterpreter())
        .register(ClassConstant.UsageType.ANNOTATION_VALUE, new AnnotationConstantUsageInterpreter())
        .build();
  }
}