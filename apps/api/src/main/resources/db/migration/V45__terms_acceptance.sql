-- The terms of service and privacy policy a member accepted (#74): which version, and when, on
-- the server's clock. NULL until the first acceptance; a new version is accepted again.
ALTER TABLE app_user ADD COLUMN terms_version INTEGER;
ALTER TABLE app_user ADD COLUMN terms_accepted_at TIMESTAMPTZ;
