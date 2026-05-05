/**
 * Application service layer — orchestrates bytecode extraction, diffing, and versioning.
 *
 * <p>Services in this package sit between the HTTP controllers and the store layer.
 * They are synchronous internally and wrap results in {@code Mono} at the boundary.
 *
 * <ul>
 *   <li>{@link org.glodean.constants.services.ExtractionService} /
 *       {@link org.glodean.constants.services.ConcreteExtractionService} — creates
 *       {@code ModelExtractor} instances for class files and JARs (via Jimfs).</li>
 *   <li>{@link org.glodean.constants.services.ExtractionServiceConfiguration} —
 *       Spring {@code @Configuration} that wires the {@code AnalysisMerger} and
 *       {@code ConstantUsageInterpreterRegistry}.</li>
 *   <li>{@link org.glodean.constants.services.ConfigFileExtractionConfiguration} —
 *       wires extractors for YAML / properties config files.</li>
 *   <li>{@link org.glodean.constants.services.ProjectVersionService} — manages
 *       project version lifecycle (creation, finalisation, removal sync).</li>
 *   <li>{@link org.glodean.constants.services.DiffService} — computes constant diffs
 *       between two versions of a project.</li>
 *   <li>{@link org.glodean.constants.services.LoggingExtractionNotifier} — logs
 *       extraction events via Log4j2.</li>
 * </ul>
 */
package org.glodean.constants.services;
