-- Display name, job-search preferences and a token version.
-- Bumping token_version (on password reset) invalidates every JWT issued before it.
ALTER TABLE users ADD COLUMN name VARCHAR(120);
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN preferred_location VARCHAR(255);
ALTER TABLE users ADD COLUMN include_remote BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN email_reminders BOOLEAN NOT NULL DEFAULT FALSE;
