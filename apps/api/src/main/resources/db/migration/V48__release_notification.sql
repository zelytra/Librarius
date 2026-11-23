-- #615: following an author or a series notifies of its next volume. A RELEASE notification is
-- about catalog data, not about a member: it has no actor, and points at the announcement
-- instead (which takes it along when it is purged).
ALTER TABLE notification ALTER COLUMN actor_id DROP NOT NULL;
ALTER TABLE notification ADD COLUMN upcoming_release_id UUID REFERENCES upcoming_release (id) ON DELETE CASCADE;

ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type
    CHECK (type IN ('ACTIVITY', 'REACTION', 'COMMENT', 'FOLLOW', 'RELEASE'));

ALTER TABLE notification DROP CONSTRAINT ck_notification_event;
ALTER TABLE notification ADD CONSTRAINT ck_notification_event
    CHECK (type IN ('FOLLOW', 'RELEASE') OR feed_event_id IS NOT NULL);

-- Every type but RELEASE is somebody's doing; RELEASE, and only it, names an announcement.
ALTER TABLE notification ADD CONSTRAINT ck_notification_actor
    CHECK (type = 'RELEASE' OR actor_id IS NOT NULL);
ALTER TABLE notification ADD CONSTRAINT ck_notification_release
    CHECK ((type = 'RELEASE') = (upcoming_release_id IS NOT NULL));

-- One piece of news per reader and announcement, however many follows lead to it.
CREATE UNIQUE INDEX uq_notification_release ON notification (user_id, upcoming_release_id)
    WHERE upcoming_release_id IS NOT NULL;
