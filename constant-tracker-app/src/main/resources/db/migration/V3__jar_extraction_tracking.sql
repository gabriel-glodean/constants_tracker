-- ============================================================
-- fat_jar_extractions: one tracking row per uploaded fat JAR
-- ============================================================
CREATE TABLE fat_jar_extractions
(
	id               BIGSERIAL    PRIMARY KEY,
	project          VARCHAR(255) NOT NULL,
	version          INT          NOT NULL,
	jar_name     VARCHAR(512) NOT NULL,
	status           VARCHAR(32)  NOT NULL,
	started_at       TIMESTAMPTZ  NOT NULL,
	last_updated_at  TIMESTAMPTZ  NOT NULL,
	nested_total     INT          NOT NULL DEFAULT 0,
	nested_processed INT          NOT NULL DEFAULT 0,
	nested_failed    INT          NOT NULL DEFAULT 0,
	error_message    TEXT
);

ALTER TABLE fat_jar_extractions
	ADD CONSTRAINT chk_fat_jar_extractions_status
		CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED'));

CREATE INDEX idx_fat_jar_extractions_project_version
	ON fat_jar_extractions (project, version, jar_name);


