package org.glodean.constants.store.postgres.entity;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity for the {@code solr_outbox_dead} table.
 *
 * Rows are moved here from {@code solr_outbox} by the nightly compaction job
 * when the maximum retry count is reached. Dead-letter rows are retained for audit
 * automatically deleted by the application.
 */
@Table("solr_outbox_dead")
public record SolrOutboxDeadEntry(
    @Id Long id,
    OffsetDateTime failedAt,
    String project,
    String unitPath,
    int version,
    String payloadJson,
    String lastError) {}
