-- Applications keep their own title/company/location/url so hand-logged entries
-- need no job row and existing rows need no backfill. job_id links an application
-- to a job in the app when there is one.
ALTER TABLE job_applications ADD COLUMN job_id BIGINT REFERENCES jobs(id) ON DELETE SET NULL;
-- SET NULL, never CASCADE: deleting an old CV must not erase the record that you applied.
ALTER TABLE job_applications ADD COLUMN resume_id BIGINT REFERENCES resumes(id) ON DELETE SET NULL;
ALTER TABLE job_applications ADD COLUMN status_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
ALTER TABLE job_applications ADD COLUMN next_action VARCHAR(255);
ALTER TABLE job_applications ADD COLUMN next_action_date DATE;
-- The next_action_date a reminder email was last sent for, so each is sent once.
ALTER TABLE job_applications ADD COLUMN reminder_sent_for DATE;

UPDATE job_applications SET status_updated_at = updated_at;

CREATE UNIQUE INDEX uq_job_applications_user_job
    ON job_applications(user_id, job_id) WHERE job_id IS NOT NULL;
CREATE INDEX idx_job_applications_next_action ON job_applications(next_action_date)
    WHERE next_action_date IS NOT NULL;

-- One row per status change. The application holds only the current status;
-- this is how employer responses are tracked over time.
CREATE TABLE application_events (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    from_status VARCHAR(50),                  -- null only for the event that created it
    to_status VARCHAR(50) NOT NULL,
    note VARCHAR(1000),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_application_events_app ON application_events(application_id, changed_at);

-- Give existing applications their creation event.
INSERT INTO application_events (application_id, from_status, to_status, changed_at)
SELECT id, NULL, status, created_at FROM job_applications;

CREATE TABLE communications (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,                -- EMAIL | CALL | MEETING | MESSAGE | OTHER
    direction VARCHAR(10) NOT NULL,           -- RECEIVED | SENT
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    contact_name VARCHAR(255),
    subject VARCHAR(500),
    summary TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_communications_app ON communications(application_id, occurred_at DESC);

CREATE TABLE interview_preps (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL UNIQUE REFERENCES job_applications(id) ON DELETE CASCADE,
    content JSONB NOT NULL,
    model_used VARCHAR(80),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
