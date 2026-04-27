package org.glodean.constants.store.postgres.entity;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
@Table("version_deletions")
public record VersionDeletionEntity(
    @Id Long id,
    String project,
    int version,
    String unitPath,
    LocalDateTime deletedAt) {}
