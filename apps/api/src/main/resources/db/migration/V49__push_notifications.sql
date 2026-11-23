-- #69: push notifications, opt-in only. Each category is off until the member switches it on.
ALTER TABLE app_user ADD COLUMN push_releases BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN push_wishlist BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN push_goal     BOOLEAN NOT NULL DEFAULT false;

-- The devices that receive them. A token names one app install; it belongs to whoever
-- registered it last, so a phone that changes hands stops receiving the previous account's.
CREATE TABLE push_device (
    token      VARCHAR(4096) PRIMARY KEY,
    user_id    VARCHAR(255)  NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    platform   VARCHAR(8)    NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_push_device_platform CHECK (platform IN ('android', 'ios'))
);
CREATE INDEX idx_push_device_user ON push_device (user_id);

-- What was already sent, one row per member and message: a re-run of the daily job, or a
-- second replica, never sends the same message twice.
CREATE TABLE push_sent (
    user_id VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    key     VARCHAR(128) NOT NULL,
    sent_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, key)
);
