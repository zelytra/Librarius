-- Per-account public visibility preference on app_user (#201).
--
-- Until now no account's content was ever readable by another: every table is scoped by a
-- single user_id and no endpoint asked "may this other caller see this?". The v1.2 social
-- features change that, and this column is the opt-out of the default rule.
--
-- The default account is private: another member sees the caller's shared reviews, reading
-- activity and library only when both directions of user_follow (V18, #200) exist -- alice
-- and bob each follow the other. A public account waives that entirely -- it is visible to
-- any signed-in member, no follow required either way. The rule itself lives in one place,
-- the VisibilityGate service; this column is only the per-account input it reads.
--
-- Defaults to false, so every existing account stays private with no backfill, and an account
-- reads as private from the moment the column exists. It is the account's own choice, set
-- through PATCH /api/me alongside the display name and locale.
ALTER TABLE app_user ADD COLUMN public_account BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN app_user.public_account IS
    'The account opted its shared content into being visible to any signed-in member (#201). '
    'Default false: content is otherwise visible only through a mutual follow';
