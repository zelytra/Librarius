-- An admin sets a member's trusted standing by hand (#625), and the admin role becomes visible
-- on a profile.
--
-- `trust_override` is the admin's choice: NULL leaves the account to the daily evaluation
-- (#180, #195) as before; true keeps it trusted, false keeps it untrusted, whatever the
-- evaluation would decide. `trusted` stays the one column every screen reads -- the override
-- only decides what is written there.
--
-- `admin` mirrors the Keycloak realm role `admin`, refreshed on each of the member's requests
-- like `username` (V40): the role lives in the token, and another member's profile has no token
-- of theirs to read it from. It defaults to false, so a member reads as no admin until their
-- next request after this migration.
ALTER TABLE app_user ADD COLUMN trust_override BOOLEAN;
ALTER TABLE app_user ADD COLUMN admin BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN app_user.trust_override IS
    'Admin override of the trust evaluation (#625): NULL automatic, true trusted, false untrusted';
COMMENT ON COLUMN app_user.admin IS
    'Mirror of the Keycloak admin role, refreshed on each request of the member (#625)';
