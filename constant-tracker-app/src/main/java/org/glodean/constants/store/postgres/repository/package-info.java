/**
 * Spring Data R2DBC reactive repository interfaces for the PostgreSQL store.
 *
 * <p>All repositories extend {@code ReactiveCrudRepository} or
 * {@code R2dbcRepository} and return {@code Mono} / {@code Flux}. Custom query
 * methods use the Spring Data query-derivation convention or {@code @Query} with
 * native SQL where derivation is insufficient.
 *
 * <ul>
 *   <li>{@link org.glodean.constants.store.postgres.repository.UnitSnapshotRepository}</li>
 *   <li>{@link org.glodean.constants.store.postgres.repository.UnitConstantRepository}</li>
 *   <li>{@link org.glodean.constants.store.postgres.repository.ConstantUsageRepository}</li>
 *   <li>{@link org.glodean.constants.store.postgres.repository.ProjectVersionRepository}</li>
 *   <li>{@link org.glodean.constants.store.postgres.repository.VersionDeletionRepository}</li>
 *   <li>{@link org.glodean.constants.store.postgres.repository.UnitDescriptorRepository}</li>
 *   <li>{@link org.glodean.constants.store.postgres.repository.SolrOutboxRepository}</li>
 * </ul>
 */
package org.glodean.constants.store.postgres.repository;
