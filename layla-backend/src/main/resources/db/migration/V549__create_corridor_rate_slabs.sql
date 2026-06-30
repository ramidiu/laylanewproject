-- ============================================================================
-- V549: Amount-based exchange-rate slabs per corridor
-- ----------------------------------------------------------------------------
-- Lets an admin define tiered rates by send amount, e.g. for GBP->SDG (Sudan):
--   1 .. 100 GBP    -> rate 5000
--   101 .. 200 GBP  -> rate 4500
-- When a quote's send amount falls inside a slab, that slab's rate is used as
-- the applied exchange rate (receive = send * rate), overriding the margin-based
-- rate. max_amount NULL = open-ended (and above). Contiguous bands recommended.
-- ============================================================================

CREATE TABLE IF NOT EXISTS corridor_rate_slabs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    corridor_id BIGINT          NOT NULL,
    min_amount  DECIMAL(18,2)   NOT NULL,
    max_amount  DECIMAL(18,2)   NULL,
    rate        DECIMAL(18,6)   NOT NULL,
    is_active   TINYINT(1)      NOT NULL DEFAULT 1,
    created_at  DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crs_corridor (corridor_id, is_active),
    CONSTRAINT fk_crs_corridor FOREIGN KEY (corridor_id) REFERENCES corridors(id) ON DELETE CASCADE
);
