-- =============================================================================
-- STEP 4 — LIVE RUN: Execute migration and COMMIT permanently.
--           !! ONLY RUN AFTER REVIEWING THE DRY RUN REPORT !!
--
-- Execute inside container:
--   docker exec -i layla-mysql mysql -uroot -proot remitz < 04_run_live.sql
-- =============================================================================

USE remitz;
SET SESSION sql_mode = '';

SELECT '>>> Starting LIVE migration — this will COMMIT data.' AS status;

CALL run_migration(0);
