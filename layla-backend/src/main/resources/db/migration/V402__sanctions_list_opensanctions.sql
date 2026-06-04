-- Extend sanctions_lists to hold OpenSanctions FtM entities.
-- Legacy seed rows remain; new columns are nullable so they do not break.

ALTER TABLE sanctions_lists
    MODIFY list_name ENUM('OFAC','EU','UN','HMT') NULL;

ALTER TABLE sanctions_lists
    ADD COLUMN external_id VARCHAR(255) NULL AFTER id,
    ADD COLUMN source_code VARCHAR(64) NULL AFTER external_id,
    ADD COLUMN list_type ENUM('SANCTIONS','PEP','CRIME','DEBARMENT','OTHER') NULL AFTER source_code,
    ADD COLUMN schema_type VARCHAR(40) NULL AFTER entry_type,
    ADD COLUMN topics JSON NULL AFTER aliases,
    ADD COLUMN nationalities JSON NULL AFTER country,
    ADD COLUMN last_seen_at TIMESTAMP NULL,
    ADD COLUMN deleted_at TIMESTAMP NULL;

CREATE UNIQUE INDEX idx_sanctions_external_id ON sanctions_lists (external_id);
CREATE INDEX idx_sanctions_list_type ON sanctions_lists (list_type);
CREATE INDEX idx_sanctions_source_code ON sanctions_lists (source_code);
CREATE INDEX idx_sanctions_last_seen_at ON sanctions_lists (last_seen_at);
CREATE INDEX idx_sanctions_deleted_at ON sanctions_lists (deleted_at);
