-- Reports (#489, #490): what a member sends from the bug button, and the errors the web app
-- and the API record by themselves. An error is one row per fingerprint, counted each time it
-- comes back. A report outlives its author's account: erasing the account forgets who sent it.
CREATE TABLE feedback (
    id           UUID PRIMARY KEY,
    user_id      VARCHAR(255) REFERENCES app_user (id) ON DELETE SET NULL,
    kind         VARCHAR(8)   NOT NULL,
    source       VARCHAR(8)   NOT NULL,
    message      TEXT         NOT NULL,
    details      TEXT,
    page         VARCHAR(1024),
    app_version  VARCHAR(64),
    user_agent   VARCHAR(512),
    fingerprint  VARCHAR(64),
    occurrences  INT          NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_feedback_kind CHECK (kind IN ('BUG', 'IDEA', 'OTHER', 'ERROR')),
    CONSTRAINT ck_feedback_source CHECK (source IN ('USER', 'WEB', 'API'))
);

CREATE UNIQUE INDEX ux_feedback_fingerprint ON feedback (fingerprint) WHERE fingerprint IS NOT NULL;
CREATE INDEX idx_feedback_last_seen ON feedback (last_seen_at DESC);
