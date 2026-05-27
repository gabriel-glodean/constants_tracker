-- ============================================================
-- Drop the composite (snapshot_id, constant_value) B-tree index
-- on unit_constants.  The constant_value column can hold strings
-- larger than Postgres' B-tree maximum (2704 bytes), which causes
-- INSERTs to fail with "index row size exceeds maximum".
--
-- The remaining idx_unit_constants_snapshot index on (snapshot_id)
-- is sufficient for all current query patterns, which only filter
-- by snapshot_id and never perform equality lookups on constant_value.
-- ============================================================
DROP INDEX IF EXISTS idx_unit_constants_snapshot_value;

