package org.glodean.constants.store.postgres.entity;

import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity for the {@code fat_jar_extractions} table.
 *
 * <p>Tracks one fire-and-forget extraction job per uploaded fat JAR.
 *
 * <p><strong>Timestamp responsibility:</strong> The application must set {@code startedAt}
 * and {@code lastUpdatedAt} explicitly. {@code startedAt} should be set when the job is created;
 * {@code lastUpdatedAt} should be refreshed whenever progress counters or status change.
 */
@Table("fat_jar_extractions")
public record JarExtractionEntity(
        @Id Long id,
        String project,
        int version,
        String jarName,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime lastUpdatedAt,
        int nestedTotal,
        int nestedProcessed,
        int nestedFailed,
        String errorMessage) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .project(project)
                .version(version)
                .jarName(jarName)
                .status(status)
                .startedAt(startedAt)
                .lastUpdatedAt(lastUpdatedAt)
                .nestedTotal(nestedTotal)
                .nestedProcessed(nestedProcessed)
                .nestedFailed(nestedFailed)
                .errorMessage(errorMessage);
    }

    public static JarExtractionEntity started(String project,
                                              int version,
                                              String jarName
    ) {
        var now = OffsetDateTime.now();
        return builder()
                .project(project)
                .version(version)
                .jarName(jarName)
                .status(JarExtractionEntity.STATUS_STARTED)
                .startedAt(now)
                .lastUpdatedAt(now)
                .nestedTotal(0)
                .nestedProcessed(0)
                .nestedFailed(0)
                .build();
    }

    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public static final class Builder {
        private Long id;
        private String project;
        private int version;
        private String jarName;
        private String status;
        private OffsetDateTime startedAt;
        private OffsetDateTime lastUpdatedAt;
        private int nestedTotal;
        private int nestedProcessed;
        private int nestedFailed;
        private String errorMessage;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder project(String project) {
            this.project = project;
            return this;
        }

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public Builder jarName(String jarName) {
            this.jarName = jarName;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder startedAt(OffsetDateTime startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder lastUpdatedAt(OffsetDateTime lastUpdatedAt) {
            this.lastUpdatedAt = lastUpdatedAt;
            return this;
        }

        public Builder nestedTotal(int nestedTotal) {
            this.nestedTotal = nestedTotal;
            return this;
        }

        public Builder nestedProcessed(int nestedProcessed) {
            this.nestedProcessed = nestedProcessed;
            return this;
        }

        public Builder nestedFailed(int nestedFailed) {
            this.nestedFailed = nestedFailed;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public JarExtractionEntity build() {
            return new JarExtractionEntity(
                    id,
                    project,
                    version,
                    jarName,
                    status,
                    startedAt,
                    lastUpdatedAt,
                    nestedTotal,
                    nestedProcessed,
                    nestedFailed,
                    errorMessage
            );
        }
    }
}
