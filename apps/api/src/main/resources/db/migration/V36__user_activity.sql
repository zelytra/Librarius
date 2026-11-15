-- Who uses the instance (#491): one row per member and day they made an authenticated
-- request, its last_seen_at moved forward at most once a minute. Members active per day are
-- its rows per day; active right now, those seen in the last five minutes.
CREATE TABLE user_activity (
    user_id      VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    day          DATE         NOT NULL,
    last_seen_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, day)
);

CREATE INDEX idx_user_activity_last_seen ON user_activity (last_seen_at);
