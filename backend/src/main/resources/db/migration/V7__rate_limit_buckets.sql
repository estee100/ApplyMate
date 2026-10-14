-- Token buckets for rate limiting, shared by every backend instance.
-- A bucket holds up to `limit` tokens and refills continuously at limit/window.
CREATE TABLE rate_limit_buckets (
    bucket_key VARCHAR(200) PRIMARY KEY,
    tokens DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_rate_limit_buckets_updated ON rate_limit_buckets(updated_at);
