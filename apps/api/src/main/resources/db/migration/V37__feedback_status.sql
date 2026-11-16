-- Triage of the reports (#495): where an admin stands with each one. A report starts NEW,
-- becomes SEEN when an admin opens it, and is closed as HANDLED or IRRELEVANT. An error marked
-- HANDLED that happens again is reopened as NEW by the insert that counts it.
ALTER TABLE feedback
    ADD COLUMN status            VARCHAR(10) NOT NULL DEFAULT 'NEW',
    ADD COLUMN status_changed_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_feedback_status CHECK (status IN ('NEW', 'SEEN', 'HANDLED', 'IRRELEVANT'));

CREATE INDEX idx_feedback_status ON feedback (status, last_seen_at DESC);
