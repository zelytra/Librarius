-- #613: the display preferences follow the account across devices (theme, bottom bar, the
-- shelf the library opens on, the reading sheet's look). One JSONB document per account: the
-- keys are owned by the web app, validated by the API, and an absent key means "nothing
-- chosen", so the device keeps its own default.
ALTER TABLE app_user ADD COLUMN preferences JSONB NOT NULL DEFAULT '{}'::jsonb;
