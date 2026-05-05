/**
 * R2DBC entity classes that map directly to PostgreSQL tables.
 *
 * <p>Each class is annotated with {@code @Table} and uses Spring Data R2DBC
 * conventions. Entities are intentionally kept as plain Java classes (not records)
 * because R2DBC requires mutable state for its reflection-based mapping.
 *
 * <p>Key entities:
 * <ul>
 *   <li>{@link org.glodean.constants.store.postgres.entity.UnitSnapshotEntity} — one row
 *       per uploaded unit per version.</li>
 *   <li>{@link org.glodean.constants.store.postgres.entity.UnitConstantEntity} — individual
 *       constant value discovered within a snapshot.</li>
 *   <li>{@link org.glodean.constants.store.postgres.entity.ConstantUsageEntity} — usage
 *       location metadata for a single constant.</li>
 *   <li>{@link org.glodean.constants.store.postgres.entity.ProjectVersionEntity} — version
 *       lifecycle record (open / finalised).</li>
 *   <li>{@link org.glodean.constants.store.postgres.entity.VersionDeletionEntity} — records
 *       units explicitly removed in a version.</li>
 *   <li>{@link org.glodean.constants.store.postgres.entity.SolrOutboxEntry} /
 *       {@link org.glodean.constants.store.postgres.entity.SolrOutboxDeadEntry} — transactional
 *       outbox for reliable Solr indexing.</li>
 * </ul>
 */
package org.glodean.constants.store.postgres.entity;
