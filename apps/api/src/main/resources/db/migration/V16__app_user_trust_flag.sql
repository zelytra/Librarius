-- Server-computed trust flag on app_user (#180).
--
-- Marks an account whose catalog contributions can be trusted. It is never set by a user --
-- neither on themselves nor on anyone else: only the application decides, from the account's
-- own activity, through `TrustEvaluator` run off the request path. Storage and the evaluation
-- mechanism land here; showing the flag (#186) and revoking it on an upheld report (#195) are
-- their own issues in this milestone.
--
-- `trusted` defaults to false and `trusted_at` stays NULL until the day the flag is first
-- earned, so an account that never qualifies needs no backfill and reads as untrusted from the
-- moment the column exists. Nothing is computed here: the evaluator fills these as it runs.
ALTER TABLE app_user ADD COLUMN trusted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN trusted_at TIMESTAMPTZ;

COMMENT ON COLUMN app_user.trusted IS
    'Server-computed: the account is trusted. Never accepted as client input (#180)';
COMMENT ON COLUMN app_user.trusted_at IS
    'When the trust flag was first earned; NULL while the account is not trusted';
