ALTER TABLE users
    ADD COLUMN demo_access_expires_at TIMESTAMP NULL;

CREATE INDEX idx_users_demo_expires_at ON users (demo_access_expires_at);
