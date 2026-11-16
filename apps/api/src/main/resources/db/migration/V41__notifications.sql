-- In-app notifications (#516, #211): what the bell lists. A row is written where the thing
-- happens — a followed reader's activity, a reaction or a comment on mine — for one recipient.
-- Every foreign key cascades: an account, an event or a comment that goes takes its
-- notifications with it.
CREATE TABLE notification (
    id            UUID         PRIMARY KEY,
    user_id       VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type          VARCHAR(10)  NOT NULL,
    actor_id      VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    feed_event_id UUID         NOT NULL REFERENCES feed_event (id) ON DELETE CASCADE,
    comment_id    UUID         REFERENCES feed_comment (id) ON DELETE CASCADE,
    emote         VARCHAR(8),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at       TIMESTAMPTZ,
    CONSTRAINT ck_notification_type CHECK (type IN ('ACTIVITY', 'REACTION', 'COMMENT'))
);

CREATE INDEX idx_notification_user_created ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_unread ON notification (user_id) WHERE read_at IS NULL;

-- Following a reader notifies of their activity unless switched off on their profile.
ALTER TABLE user_follow ADD COLUMN notify BOOLEAN NOT NULL DEFAULT true;

-- Comments on my activity notify me unless switched off in Settings (#211).
ALTER TABLE app_user ADD COLUMN notify_comments BOOLEAN NOT NULL DEFAULT true;
