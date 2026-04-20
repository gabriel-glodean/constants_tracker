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

