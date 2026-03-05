/**
 * Data model representing constants discovered through bytecode analysis.
 *
 * <p>This package contains the core domain model for constant usage tracking:
 * <ul>
 *   <li>{@link org.glodean.constants.model.ClassConstants}: Top-level result container</li>
 *   <li>{@link org.glodean.constants.model.ClassConstant}: Individual constant with usage metadata</li>
 *   <li>{@link org.glodean.constants.model.ClassConstant.UsageType}: Structural usage classification</li>
 *   <li>{@link org.glodean.constants.model.ClassConstant.SemanticType}: Domain-level semantic classification</li>
 *   <li>{@link org.glodean.constants.model.ClassConstant.ConstantUsage}: Detailed usage context</li>
 *   <li>{@link org.glodean.constants.model.ClassConstant.UsageLocation}: Bytecode location information</li>
 * </ul>
 *
 * <p><b>Design principles:</b>
 * <ul>
 *   <li>Immutable records for thread-safety and functional composition</li>
 *   <li>Two-tier classification: structural (bytecode-level) + semantic (domain-level)</li>
 *   <li>Extensible semantic types via {@link org.glodean.constants.model.ClassConstant.CustomSemanticType}</li>
 *   <li>Confidence scoring for uncertain classifications</li>
 * </ul>
 *
 * <p><b>Example usage:</b>
 * <pre>{@code
 * // Create a constant usage observation
 * var usage = new ClassConstant.ConstantUsage(
 *     ClassConstant.UsageType.METHOD_INVOCATION_PARAMETER,
 *     ClassConstant.CoreSemanticType.SQL_FRAGMENT,
 *     new ClassConstant.UsageLocation(
 *         "com/example/UserRepository",
 *         "findAll",
 *         "()Ljava/util/List;",
 *         42,
 *         null
 *     ),
 *     0.94
 * );
 *
 * // Package into ClassConstants result
 * var constant = new ClassConstant("SELECT * FROM users", Set.of(usage));
 * var results = new ClassConstants("com/example/UserRepository", Set.of(constant));
 * }</pre>
 *
 * @see org.glodean.constants.extractor.ModelExtractor
 */
package org.glodean.constants.model;

