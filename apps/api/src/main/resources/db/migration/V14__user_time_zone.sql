-- The user's time zone, added to the editable profile (#75).
--
-- `app_user` already carried `display_name` and `locale`; the profile screen now edits both
-- and adds a time zone, so the greeting and the date formatting can follow the reader's own
-- clock instead of a hardcoded `fr-FR`. Nullable, and left NULL for everyone who never sets
-- one: a null zone means "use the device's", which is the right default and needs no backfill.
-- Stored as an IANA identifier (`Europe/Paris`), validated as a `java.time.ZoneId` at the API.
ALTER TABLE app_user ADD COLUMN time_zone VARCHAR(64);

COMMENT ON COLUMN app_user.time_zone IS
    'IANA time-zone id (e.g. Europe/Paris); NULL falls back to the client zone';
