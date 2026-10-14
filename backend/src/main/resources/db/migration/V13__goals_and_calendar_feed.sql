-- Weekly application goal for the dashboard streak.
ALTER TABLE users ADD COLUMN weekly_goal INTEGER NOT NULL DEFAULT 5;
-- Secret for the private calendar feed URL, stored only as a SHA-256 hash.
ALTER TABLE users ADD COLUMN calendar_token_hash VARCHAR(64) UNIQUE;
