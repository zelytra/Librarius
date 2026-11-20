-- A new follower is announced (#544): a FOLLOW notification is about the follower themselves,
-- not about a feed event, so the event becomes optional for that one type.
ALTER TABLE notification ALTER COLUMN feed_event_id DROP NOT NULL;

ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type
    CHECK (type IN ('ACTIVITY', 'REACTION', 'COMMENT', 'FOLLOW'));

-- Every other type still points at its event.
ALTER TABLE notification ADD CONSTRAINT ck_notification_event
    CHECK (type = 'FOLLOW' OR feed_event_id IS NOT NULL);
