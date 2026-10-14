-- Profile pictures, stored as a re-encoded 256px PNG (so no EXIF or GPS metadata
-- survives). A separate table keeps the bytes off every users query.
CREATE TABLE user_avatars (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    image BYTEA NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
