-- Replace case-sensitive unique constraint with case-insensitive unique index.
-- Prevents duplicate accounts via concurrent registration with different casing.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
DROP INDEX IF EXISTS idx_users_email_lower;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_ci
    ON users(LOWER(email));
