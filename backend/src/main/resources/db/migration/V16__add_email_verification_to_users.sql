ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN verification_code VARCHAR(10),
    ADD COLUMN verification_code_expires_at TIMESTAMP;

-- Backfill existing users so existing accounts remain verified
UPDATE users SET email_verified = TRUE;
