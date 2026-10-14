-- Full-text search. Generated tsvector columns keep themselves in sync with every
-- insert and update, weighted so a match in the title outranks one in the body.
ALTER TABLE jobs ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(company, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(location, '')), 'C') ||
    setweight(to_tsvector('english', coalesce(left(description, 20000), '')), 'D')
) STORED;
CREATE INDEX idx_jobs_search ON jobs USING GIN (search_vector);

ALTER TABLE job_applications ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('english', coalesce(job_title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(company_name, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(location, '')), 'C') ||
    setweight(to_tsvector('english', coalesce(notes, '') || ' ' || coalesce(next_action, '')), 'D')
) STORED;
CREATE INDEX idx_job_applications_search ON job_applications USING GIN (search_vector);
