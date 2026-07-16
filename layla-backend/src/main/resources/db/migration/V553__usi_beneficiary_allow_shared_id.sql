-- USI Money dedups beneficiaries by person, so the same usi_beneficiary_id can legitimately map to
-- multiple local beneficiaries (e.g. the same recipient added more than once across transactions).
-- The UNIQUE KEY on usi_beneficiary_id made linking the 2nd local beneficiary fail with
-- "Duplicate entry 'NNN' for key uk_usi_benef_id", which 500'd USI transaction creation.
-- Drop that unique key. The UNIQUE KEY on local_beneficiary_id stays (each local beneficiary still
-- maps to exactly one USI beneficiary).
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = 'usi_beneficiaries'
               AND index_name = 'uk_usi_benef_id');
SET @sql := IF(@idx > 0, 'ALTER TABLE usi_beneficiaries DROP INDEX uk_usi_benef_id', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
