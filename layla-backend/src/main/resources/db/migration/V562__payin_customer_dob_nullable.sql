-- Date of birth is not always known when a regular user is auto-registered as a
-- pay-in customer (migrated users often have no DoB yet). The code used to write
-- a fake placeholder '1990-01-01' to satisfy the NOT NULL constraint, which then
-- surfaced in the USI CSV export as the same bogus DoB for many different people.
--
-- Make the column nullable so the placeholder is no longer needed; unknown DoB is
-- now stored as NULL and the export shows it blank (honest) instead of fake.
ALTER TABLE payin_customers
    MODIFY COLUMN dob DATE NULL;

-- Clean up any placeholder values already written by the old code so historical
-- pay-in rows stop reporting the bogus 1990-01-01. (Real 1990-01-01 birthdays are
-- rare here and were never captured through KYC — these are all placeholders.)
UPDATE payin_customers
   SET dob = NULL
 WHERE dob = '1990-01-01';
