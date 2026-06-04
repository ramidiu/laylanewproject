-- Adds the full sender profile, document, payout routing, and currency fields
-- required by Amal Express v3 GenerateCashPickupTransaction / GenerateBankTransaction
-- / GenerateMobileWalletTransaction endpoints.
--
-- Existing rows remain valid: all new columns are nullable.

ALTER TABLE amal_transactions
    -- Numeric Amal country IDs (complement the existing ISO string columns)
    ADD COLUMN destination_country_id VARCHAR(20)   NULL AFTER destination_country,
    ADD COLUMN source_country_id      VARCHAR(20)   NULL AFTER destination_country_id,

    -- Currency and amount breakdown
    ADD COLUMN sending_currency       VARCHAR(10)   NULL AFTER currency,
    ADD COLUMN currency_to_receive    VARCHAR(10)   NULL AFTER sending_currency,
    ADD COLUMN amount_to_receive      DECIMAL(15,2) NULL AFTER currency_to_receive,
    ADD COLUMN total_amount           DECIMAL(15,2) NULL AFTER amount_to_receive,

    -- Sender profile
    ADD COLUMN sender_fname                  VARCHAR(100) NULL AFTER beneficiary_name,
    ADD COLUMN sender_lname                  VARCHAR(100) NULL AFTER sender_fname,
    ADD COLUMN sender_dob                    VARCHAR(30)  NULL AFTER sender_lname,
    ADD COLUMN sender_nationality            VARCHAR(100) NULL AFTER sender_dob,
    ADD COLUMN sender_city                   VARCHAR(100) NULL AFTER sender_nationality,
    ADD COLUMN sender_postal_code            VARCHAR(20)  NULL AFTER sender_city,
    ADD COLUMN sender_mobile                 VARCHAR(50)  NULL AFTER sender_postal_code,
    ADD COLUMN sender_mobile_country_code    VARCHAR(10)  NULL AFTER sender_mobile,
    ADD COLUMN source_branch_key             VARCHAR(100) NULL AFTER sender_mobile_country_code,
    ADD COLUMN source_of_income              VARCHAR(100) NULL AFTER source_branch_key,
    ADD COLUMN purpose                       VARCHAR(100) NULL AFTER source_of_income,

    -- Sender ID document
    ADD COLUMN document_name    VARCHAR(100) NULL AFTER purpose,
    ADD COLUMN document_number  VARCHAR(100) NULL AFTER document_name,
    ADD COLUMN date_of_issue    VARCHAR(30)  NULL AFTER document_number,
    ADD COLUMN date_of_expire   VARCHAR(30)  NULL AFTER date_of_issue,
    ADD COLUMN issuer           VARCHAR(100) NULL AFTER date_of_expire,

    -- Payout routing
    ADD COLUMN service_id                    VARCHAR(20)  NULL AFTER service_operator,
    ADD COLUMN service_operator_id           VARCHAR(20)  NULL AFTER service_id,
    ADD COLUMN payout_method                 VARCHAR(50)  NULL AFTER service_operator_id,
    ADD COLUMN collection_mode               VARCHAR(50)  NULL AFTER payout_method,
    ADD COLUMN destination_city_id           VARCHAR(20)  NULL AFTER collection_mode,
    ADD COLUMN beneficiary_mobile_country_code VARCHAR(10) NULL AFTER mobile_number,

    -- Misc
    ADD COLUMN transaction_date      VARCHAR(50)  NULL AFTER updated_at,
    ADD COLUMN user_created          VARCHAR(100) NULL AFTER transaction_date;
