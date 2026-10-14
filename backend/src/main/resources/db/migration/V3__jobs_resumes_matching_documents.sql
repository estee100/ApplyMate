-- Jobs are shared rows: two users pasting the same link, or two feed searches
-- returning the same listing, reuse one row. Per-user state lives in the join tables.
CREATE TABLE jobs (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(20) NOT NULL,              -- ADZUNA | HIMALAYAS | PASTED
    external_id VARCHAR(768) NOT NULL,        -- source id, or a SHA-256 of the pasted URL/text
    title VARCHAR(500) NOT NULL,
    company VARCHAR(255) NOT NULL,
    company_logo_url VARCHAR(2048),
    location VARCHAR(255),
    salary VARCHAR(120),
    url VARCHAR(2048),
    publisher VARCHAR(120),
    job_type VARCHAR(20),
    posted_date DATE,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_jobs_source_external UNIQUE (source, external_id)
);
CREATE INDEX idx_jobs_posted ON jobs(posted_date DESC);

-- Jobs a user pasted in (distinct from saved: everything pasted lands here).
CREATE TABLE user_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_jobs UNIQUE (user_id, job_id)
);

CREATE TABLE saved_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_saved_jobs UNIQUE (user_id, job_id)
);

-- Uploaded CVs. Text is extracted once on upload and the file itself is not kept,
-- so there is no object storage to run. Old resumes are deactivated, not deleted,
-- because matches and documents reference the resume they were built from.
CREATE TABLE resumes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    text TEXT NOT NULL,
    master_content JSONB,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_resumes_user_active ON resumes(user_id, active);

CREATE TABLE skill_profiles (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL UNIQUE REFERENCES resumes(id) ON DELETE CASCADE,
    skills JSONB NOT NULL DEFAULT '[]',
    job_titles JSONB NOT NULL DEFAULT '[]',
    domains JSONB NOT NULL DEFAULT '[]',
    locations JSONB NOT NULL DEFAULT '[]',
    seniority VARCHAR(60),
    years_experience NUMERIC(4, 1),
    summary TEXT,
    model_used VARCHAR(80),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- AI match analysis, cached per (user, job, resume) so reopening a job is free
-- and a new resume gets a fresh score.
CREATE TABLE job_matches (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    resume_id BIGINT NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    match_percentage INTEGER NOT NULL CHECK (match_percentage BETWEEN 0 AND 100),
    requirements_met JSONB NOT NULL DEFAULT '[]',
    requirements_missing JSONB NOT NULL DEFAULT '[]',
    rationale TEXT,
    model_used VARCHAR(80),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_job_matches UNIQUE (user_id, job_id, resume_id)
);

-- content is the original output and is never overwritten; edited_content holds
-- the user's revisions, so "reset to original" always works.
CREATE TABLE generated_documents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    resume_id BIGINT REFERENCES resumes(id) ON DELETE SET NULL,
    kind VARCHAR(20) NOT NULL,                -- RESUME | COVER_LETTER
    content JSONB NOT NULL,
    edited_content JSONB,
    model_used VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_generated_documents_user_job ON generated_documents(user_id, job_id);
