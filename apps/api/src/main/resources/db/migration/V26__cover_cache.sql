-- Resized cover thumbnails served by the cover proxy (#407).
-- A NULL content records a miss, so a record without a cover is not fetched on every display.
CREATE TABLE cover_cache (
    cache_key  VARCHAR(64) PRIMARY KEY,
    content    BYTEA,
    fetched_at TIMESTAMPTZ NOT NULL
);
