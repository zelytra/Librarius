-- Attribute a report to the account it reflects on, so trust can be revoked automatically (#195).
--
-- #180 grants trust; #192 lets any member report a shared catalog object. Nothing yet closes the
-- loop the specification asks for -- "the application can revoke a trusted user if it finds too
-- many reports matching them" -- so an account that repeatedly contributes bad data keeps its
-- badge forever. `TrustEvaluator` now also revokes: an account carrying too many *upheld* reports
-- against what it contributed loses the flag. This migration gives the report table the one thing
-- that signal was missing.
--
-- `contributor_id` names who a report reflects on. `report` already carries `reporter_id` (who
-- filed it); revocation needs the other side -- the account that contributed the flagged object.
-- It is left NULL here and stays NULL until the contribution attribution (#198) records who
-- contributed a catalog object and stamps it at report time. So no real row triggers a revocation
-- yet, and the mechanism is exercised by tests that set it directly; production keeps only
-- granting until #198 wires attribution and a moderation surface (below) upholds a report.
--
-- ON DELETE SET NULL, not CASCADE like `reporter_id`: erasing the contributor's account must not
-- erase catalog feedback about the object it contributed, only forget who the report was against.
--
-- UPHELD, meanwhile, joins the `status` picklist (`domain.ReportStatus`). `status` is a VARCHAR
-- with no CHECK constraint, so storing the new value needs no column change -- only the enum in
-- code. A report counts against a contributor solely once it is UPHELD, so an unreviewed OPEN
-- report cannot on its own cost anyone their trust. Setting UPHELD is a moderation action behind
-- an admin surface a maintainer must set up (a Keycloak-gated role, deliberately NOT built here);
-- until it exists the count is always zero and trust is never revoked in production.

ALTER TABLE report
    ADD COLUMN contributor_id VARCHAR(255) REFERENCES app_user (id) ON DELETE SET NULL;

COMMENT ON COLUMN report.contributor_id IS
    'The account whose contribution this report reflects on; NULL until #198 attributes it. Read by the automatic trust revocation (#195), never client input';

-- The revocation counts a contributor's upheld reports over a rolling window, so the lookup rides
-- on contributor_id; without it the per-contributor count falls back to a sequential scan.
CREATE INDEX idx_report_contributor ON report (contributor_id);
