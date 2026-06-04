-- =============================================================================
-- STEP 3 — DRY RUN: Simulate migration, show validation report, then ROLLBACK.
--           No data is permanently changed.
--
-- Execute inside container:
--   docker exec -i layla-mysql mysql -uroot -proot remitz < 03_run_dryrun.sql
-- =============================================================================

USE remitz;
SET SESSION sql_mode = '';

SELECT '>>> Starting DRY RUN migration...' AS status;

CALL run_migration(1);
