-- ============================================================
-- unit_descriptors: identity of a source artifact per project/version
-- ============================================================
CREATE TABLE unit_descriptors
(
    id           BIGSERIAL    PRIMARY KEY,
    project      VARCHAR(255) NOT NULL,
    version      INT          NOT NULL,
    source_kind  VARCHAR(50)  NOT NULL,
    path         VARCHAR(512) NOT NULL,
    size_bytes   BIGINT       NOT NULL DEFAULT -1,
    content_hash VARCHAR(128),
    UNIQUE (project, version, path)
);

CREATE INDEX idx_unit_descriptors_project_version ON unit_descriptors (project, version);
CREATE INDEX idx_unit_descriptors_content_hash ON unit_descriptors (content_hash) WHERE content_hash IS NOT NULL;

-- ============================================================
-- unit_snapshots: extracted constants for a single unit (class, config, etc.)
-- belonging to a descriptor
-- ============================================================
CREATE TABLE unit_snapshots
(
    id                  BIGSERIAL PRIMARY KEY,
    descriptor_id       BIGINT       NOT NULL REFERENCES unit_descriptors (id) ON DELETE CASCADE,
    unit_name           VARCHAR(512) NOT NULL,
    unit_constants_json TEXT         NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_unit_snapshots_descriptor ON unit_snapshots (descriptor_id);
CREATE UNIQUE INDEX idx_unit_snapshots_descriptor_name ON unit_snapshots (descriptor_id, unit_name);

-- ============================================================
-- unit_constants: individual constant values within a snapshot
-- ============================================================
CREATE TABLE unit_constants
(
    id                  BIGSERIAL   PRIMARY KEY,
    snapshot_id         BIGINT      NOT NULL REFERENCES unit_snapshots (id) ON DELETE CASCADE,
    constant_value      TEXT        NOT NULL,
    constant_value_type VARCHAR(50) NOT NULL
);

CREATE INDEX idx_unit_constants_snapshot ON unit_constants (snapshot_id);
CREATE INDEX idx_unit_constants_snapshot_value ON unit_constants (snapshot_id, constant_value);

-- ============================================================
-- constant_usages: usage observations for a constant
-- ============================================================
CREATE TABLE constant_usages
(
    id                         BIGSERIAL        PRIMARY KEY,
    constant_id                BIGINT           NOT NULL REFERENCES unit_constants (id) ON DELETE CASCADE,
    structural_type            VARCHAR(50)      NOT NULL,
    semantic_type_kind         VARCHAR(10)      NOT NULL,
    semantic_type_name         VARCHAR(100),
    semantic_display_name      VARCHAR(255),
    semantic_description       TEXT,
    location_class_name        VARCHAR(512)     NOT NULL,
    location_method_name       VARCHAR(255)     NOT NULL,
    location_method_descriptor TEXT             NOT NULL,
    location_bytecode_offset   INT,
    location_line_number       INT,
    confidence                 DOUBLE PRECISION NOT NULL,
    metadata                   TEXT             NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_constant_usages_constant ON constant_usages (constant_id);

-- ============================================================
-- project_versions: tracks version lifecycle and inheritance chain
-- ============================================================
CREATE TABLE project_versions
(
    id             BIGSERIAL    PRIMARY KEY,
    project        VARCHAR(255) NOT NULL,
    version        INT          NOT NULL,
    parent_version INT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    finalized_at   TIMESTAMP,
    UNIQUE (project, version)
);

CREATE INDEX idx_project_versions_project ON project_versions (project);
CREATE INDEX idx_project_versions_project_status ON project_versions (project, status);

-- ============================================================
-- Add constraint to enforce that parent_version < version
-- ============================================================
ALTER TABLE project_versions
    ADD CONSTRAINT check_parent_version_less_than_version
        CHECK (parent_version IS NULL OR parent_version < version);


-- ============================================================
-- version_deletions: units explicitly removed from a version
-- (prevents inheritance from parent for these paths)
-- ============================================================
CREATE TABLE version_deletions
(
    id         BIGSERIAL    PRIMARY KEY,
    project    VARCHAR(255) NOT NULL,
    version    INT          NOT NULL,
    unit_path  VARCHAR(512) NOT NULL,
    deleted_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (project, version, unit_path)
);

CREATE INDEX idx_version_deletions_project_version ON version_deletions (project, version);

-- ============================================================
-- solr_outbox: pending Solr updates to be processed asynchronously
-- by a worker
-- ============================================================
CREATE TABLE solr_outbox (
                             id              BIGSERIAL PRIMARY KEY,
                             created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                             project         TEXT        NOT NULL,
                             unit_path       TEXT        NOT NULL,
                             version         INT         NOT NULL,
                             payload_json    TEXT        NOT NULL,   -- serialised SolrInputDocument fields
                             attempts        INT         NOT NULL DEFAULT 0,
                             last_error      TEXT,
                             next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX solr_outbox_ready_idx ON solr_outbox (next_attempt_at)
    WHERE attempts < 10;

CREATE TABLE solr_outbox_dead (
                                  id           BIGINT PRIMARY KEY,
                                  failed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  project      TEXT,
                                  unit_path    TEXT,
                                  version      INT,
                                  payload_json TEXT,
                                  last_error   TEXT
);