-- The Keycloak username (#499), so the admins' list of members can show it. Written when the
-- account is created and refreshed at the next request if it changed in Keycloak; null for an
-- account that has not signed in since this migration.
ALTER TABLE app_user ADD COLUMN username VARCHAR(255);
