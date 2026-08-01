-- Let a member flag an error in a shared catalog object (#192).
--
-- Nothing today lets a reader say that a title's data is wrong -- a swapped cover, a wrong
-- author, a duplicate work. This adds a single, generic report table, write-only from the
-- client's point of view: nothing reads a report back through the API. It is the foundation
-- the automatic trust revocation (#195) consumes, and `status` is a column left for a future
-- admin view rather than something this migration builds a screen for -- OPEN is the only
-- value written here.
--
-- A report is neither catalog data nor quite user data: it is a private signal from one user
-- to the application. `reporter_id` carries its author, `ON DELETE CASCADE` like every other
-- row hanging off `app_user` (#73), so erasing an account erases the reports it filed and the
-- reporter is never left dangling.
--
-- `target_type` + `target_id` name what is reported. There is deliberately no foreign key on
-- `target_id`: it points at `work`, `edition` or `series` depending on `target_type`, which a
-- single column cannot reference. The resource resolves the target against the matching table
-- before inserting instead, so an unknown target is a 400 rather than a silent success -- the
-- one integrity check a real FK would have given for free, moved into the service.

CREATE TABLE report (
    id          UUID PRIMARY KEY,
    reporter_id VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    target_type VARCHAR(16)  NOT NULL,   -- WORK | EDITION | SERIES (domain.ReportTargetType)
    target_id   UUID         NOT NULL,
    reason      VARCHAR(32)  NOT NULL,   -- domain.ReportReason
    comment     TEXT,
    status      VARCHAR(16)  NOT NULL DEFAULT 'OPEN',  -- domain.ReportStatus
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The revocation consumer (#195) and any future admin view read reports by what they point
-- at -- "the reports on this work" -- so the lookup rides on (target_type, target_id).
CREATE INDEX idx_report_target ON report (target_type, target_id);

-- Reached the other way for the account-deletion cascade above, and for the "who filed the
-- most reports" a future admin view would want; without it both fall back to a sequential
-- scan.
CREATE INDEX idx_report_reporter ON report (reporter_id);
