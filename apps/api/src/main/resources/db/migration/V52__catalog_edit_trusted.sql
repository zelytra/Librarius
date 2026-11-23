-- A trusted member's catalog edit applies at once (#631), like an admin's, and stays listed for
-- the admins to revert. `applied_by_trust` records that it went through on the author's trusted
-- standing rather than an admin's review or a store-page check, so the admin view can say so.
ALTER TABLE catalog_edit ADD COLUMN applied_by_trust BOOLEAN NOT NULL DEFAULT false;
