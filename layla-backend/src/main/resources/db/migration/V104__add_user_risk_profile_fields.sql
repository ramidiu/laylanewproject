-- Add email_verified flag to users table for 2-stage registration
-- These columns may already exist if auth-service V3 migration ran first (shared DB)
-- Using IGNORE to avoid errors on duplicate column

-- Procedure to safely add columns if they don't exist
DELIMITER //
CREATE PROCEDURE add_columns_if_not_exist()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'email_verified') THEN
        ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE AFTER mfa_secret;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'nationality') THEN
        ALTER TABLE users ADD COLUMN nationality VARCHAR(100) AFTER country;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'country_of_residence') THEN
        ALTER TABLE users ADD COLUMN country_of_residence VARCHAR(100) AFTER nationality;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'country_code') THEN
        ALTER TABLE users ADD COLUMN country_code VARCHAR(10) AFTER country_of_residence;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'gender') THEN
        ALTER TABLE users ADD COLUMN gender VARCHAR(20) AFTER date_of_birth;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'occupation') THEN
        ALTER TABLE users ADD COLUMN occupation VARCHAR(100) AFTER gender;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'risk_score') THEN
        ALTER TABLE users ADD COLUMN risk_score VARCHAR(20) DEFAULT 'MEDIUM' AFTER occupation;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'risk_points') THEN
        ALTER TABLE users ADD COLUMN risk_points INT DEFAULT 0 AFTER risk_score;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'risk_override') THEN
        ALTER TABLE users ADD COLUMN risk_override VARCHAR(20) AFTER risk_points;
    END IF;
END //
DELIMITER ;

CALL add_columns_if_not_exist();
DROP PROCEDURE IF EXISTS add_columns_if_not_exist;
