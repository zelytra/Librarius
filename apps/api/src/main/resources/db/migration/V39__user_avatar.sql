-- Profile pictures (#500). The picture lives in the database, as the cover thumbnails do (V26):
-- re-encoded server-side to a 256-pixel square JPEG it weighs a few tens of kilobytes, so the
-- backups and the GDPR erasure cover it with nothing else to run or secure. `avatar_version`
-- on app_user says whether there is one, and changes with it so a screen can drop a stale copy
-- without reading the table.
ALTER TABLE app_user ADD COLUMN avatar_version BIGINT;

CREATE TABLE user_avatar (
    user_id    VARCHAR(255) PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    content    BYTEA        NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
