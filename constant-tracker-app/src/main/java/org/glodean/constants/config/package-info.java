/**
 * Infrastructure Spring {@code @Configuration} classes that do not belong to a
 * specific feature package.
 *
 * <ul>
 *   <li>{@link org.glodean.constants.config.MetricsConfiguration} — registers
 *       Micrometer metric binders and custom tags for Prometheus scraping.</li>
 *   <li>{@link org.glodean.constants.config.ReactiveTransactionConfiguration} —
 *       enables reactive (R2DBC) transaction management for the PostgreSQL store.</li>
 * </ul>
 */
package org.glodean.constants.config;
