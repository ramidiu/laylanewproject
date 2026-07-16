-- Forces a password change on first login for backend customers created with a
-- default password (FIRSTNAME + first 4 digits of phone). Cleared once the user
-- changes their password (change-password page) or resets it via forgot-password.
ALTER TABLE users
    ADD COLUMN password_change_required TINYINT(1) NOT NULL DEFAULT 0;
