-- Give beneficiaries their own city field so the payout no longer reuses the street
-- address as the city (which produced wrong values like "King Saud" in the city field).
ALTER TABLE beneficiaries ADD COLUMN city VARCHAR(100) NULL AFTER address;
