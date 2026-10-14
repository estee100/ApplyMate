-- Version columns for optimistic locking: an update carrying a stale version is
-- rejected (409) instead of silently overwriting a newer change from another tab.
ALTER TABLE job_applications ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE generated_documents ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE resumes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
