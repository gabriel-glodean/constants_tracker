-- Full-fidelity store: class snapshots, constants, and usage details
CREATE TABLE class_snapshots
(
    id         BIGSERIAL PRIMARY KEY,
    project    VARCHAR(255) NOT NULL,
    class_name VARCHAR(512) NOT NULL,
    version    INT          NOT NULL,
    UNIQUE (project, class_name, version)
);

CREATE TABLE class_constants
(
    id                  BIGSERIAL PRIMARY KEY,
    snapshot_id         BIGINT       NOT NULL REFERENCES class_snapshots (id) ON DELETE CASCADE,
    constant_value      TEXT         NOT NULL,
    constant_value_type VARCHAR(50)  NOT NULL
);

CREATE INDEX idx_class_constants_snapshot ON class_constants (snapshot_id);

CREATE TABLE constant_usages
(
    id                        BIGSERIAL PRIMARY KEY,
    constant_id               BIGINT           NOT NULL REFERENCES class_constants (id) ON DELETE CASCADE,
    structural_type           VARCHAR(50)      NOT NULL,
    semantic_type_kind        VARCHAR(10)      NOT NULL, -- 'CORE' or 'CUSTOM'
    semantic_type_name        VARCHAR(100),              -- CoreSemanticType.name() or CustomSemanticType.category()
    semantic_display_name     VARCHAR(255),              -- only for CUSTOM
    semantic_description      TEXT,                      -- only for CUSTOM
    location_class_name       VARCHAR(512)     NOT NULL,
    location_method_name      VARCHAR(255)     NOT NULL,
    location_method_descriptor TEXT            NOT NULL,
    location_bytecode_offset  INT,
    location_line_number      INT,
    confidence                DOUBLE PRECISION NOT NULL,
    metadata                  TEXT             NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_constant_usages_constant ON constant_usages (constant_id);

