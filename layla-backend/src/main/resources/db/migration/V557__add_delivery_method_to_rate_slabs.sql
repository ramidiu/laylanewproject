-- ============================================================================
-- V557: Per-payment-type exchange rates. Add delivery_method to corridor_rate_slabs
--       so a corridor can carry SEPARATE absolute rates for cash vs bank vs mobile
--       (e.g. GBP->INR: bank 106, cash 103, mobile 104). A single open-ended band
--       (min 0, max NULL) per payment type acts as that type's flat rate; multiple
--       bands give amount-tiered rates per payment type.
--       delivery_method NULL = corridor-wide slab that applies to ALL payment types
--       (backward compatible: every existing slab stays NULL and keeps working).
--       Resolution order in QuoteService: payment-type slab -> NULL corridor-wide
--       slab -> margin-based rate.
-- ============================================================================

ALTER TABLE corridor_rate_slabs
    ADD COLUMN delivery_method VARCHAR(30) NULL AFTER corridor_id;

-- Speeds up the per-(corridor, payment-type) lookup done on every quote.
CREATE INDEX idx_rate_slab_corridor_method
    ON corridor_rate_slabs (corridor_id, delivery_method);
