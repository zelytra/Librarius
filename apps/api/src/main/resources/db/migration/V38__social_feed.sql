-- The social feed (#209, #210): an append-only log of what members did, so that reactions
-- and comments have a stable row to point at — a status column has no identity of its own.
--
-- An event is about one edition: a title finished, wished, or its review shared. The same
-- event is logged once per member, type and edition — finishing a book again, or editing a
-- review already shared, adds nothing. Every foreign key cascades (#73): erasing an account
-- removes the events it made, its reactions and its comments; an edition removed from the
-- catalog takes its events with it.
CREATE TABLE feed_event (
    id         UUID         PRIMARY KEY,
    user_id    VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type       VARCHAR(20)  NOT NULL,
    edition_id UUID         NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_feed_event_type CHECK (type IN ('FINISHED_READING', 'WISHLIST_ADDED', 'REVIEW_SHARED')),
    CONSTRAINT ux_feed_event UNIQUE (user_id, type, edition_id)
);

CREATE INDEX idx_feed_event_user_created ON feed_event (user_id, created_at DESC);

-- One reaction per member per event: changing it replaces the row.
CREATE TABLE feed_reaction (
    feed_event_id UUID         NOT NULL REFERENCES feed_event (id) ON DELETE CASCADE,
    user_id       VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    emote         VARCHAR(8)   NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (feed_event_id, user_id),
    CONSTRAINT ck_feed_reaction_emote CHECK (emote IN ('LOVE', 'FUNNY', 'WOW', 'CLAP', 'SAD'))
);

-- Flat comments, no threading.
CREATE TABLE feed_comment (
    id            UUID         PRIMARY KEY,
    feed_event_id UUID         NOT NULL REFERENCES feed_event (id) ON DELETE CASCADE,
    user_id       VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    comment       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_feed_comment_event ON feed_comment (feed_event_id, created_at);
