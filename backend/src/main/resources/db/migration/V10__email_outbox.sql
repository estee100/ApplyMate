-- Transactional outbox for email. A message is inserted in the same transaction as
-- the change that caused it, then delivered by a worker with retries, so an SMTP
-- outage or a crash between commit and send never loses (or double-sends) mail.
CREATE TABLE outbox_emails (
    id BIGSERIAL PRIMARY KEY,
    to_address VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',   -- PENDING | SENT | FAILED
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(500),
    dedupe_key VARCHAR(200) UNIQUE,                  -- e.g. one reminder per application per date
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE
);
-- The worker's query: due pending messages, oldest first.
CREATE INDEX idx_outbox_due ON outbox_emails(next_attempt_at, id) WHERE status = 'PENDING';
