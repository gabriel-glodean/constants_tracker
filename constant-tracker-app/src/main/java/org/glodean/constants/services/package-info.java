/**
 * Application service layer — orchestrates bytecode extraction, diffing, and versioning.
 *
 * <p>Services in this package sit between the HTTP controllers and the store layer.
 * They are synchronous internally and wrap results in {@code Mono} at the boundary.
 *
 * <ul>
 *   <li>{@link org.glodean.constants.services.ExtractionService} /
 *       {@link org.glodean.constants.services.ConcreteExtractionService} — extracts constants
 *       from class files and JARs (streamed to a temp file, opened as a native ZipFileSystem).</li>
  *   <li>{@link org.glodean.constants.services.ExtractionServiceConfiguration} —
 *       Spring {@code @Configuration} that wires the {@code AnalysisMerger},
 *       {@code ConstantUsageInterpreterRegistry}, and the unified
 *       {@link org.glodean.constants.extractor.ModelExtractorSupplierRepository} covering
 *       class files, YAML, and properties.</li>
 *   <li>{@link org.glodean.constants.services.ProjectVersionService}
 *       project version lifecycle (creation, finalisation, removal sync).</li>
 *   <li>{@link org.glodean.constants.services.DiffService} — computes constant diffs
 *       between two versions of a project.</li>
 *   <li>{@link org.glodean.constants.services.LoggingExtractionNotifier} — logs
 *       extraction events via Log4j2.</li>
 * </ul>
 */
package org.glodean.constants.services;
