-- The first link between two accounts (#200).
--
-- Until now no row in the schema pointed one app_user at another: CurrentUser only ever
-- reads the caller's own id, and every table is scoped by a single user_id. Following adds
-- that missing edge — who follows whom — the substrate the v1.2 social features build on.
--
-- One-directional and immediate: following is not a request that has to be accepted, and a
-- "friend" is simply the case where both sides follow each other (the mutual-follow
-- visibility gate #201 reads off that pair). This is distinct from series_follow and
-- author_follow, which link a user to a catalog entity; this links a user to another user.
--
-- No surrogate key: the (follower, followee) pair is the identity, and the primary key
-- doubles as the index for "the accounts this user follows", exactly the shape series_follow
-- uses (V4). Both foreign keys are ON DELETE CASCADE (#73), so deleting an account removes
-- the follows it issued and the follows pointing at it in the same statement.

CREATE TABLE user_follow (
    follower_id VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    followee_id VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    -- Nobody follows themselves: the API refuses such a request with 400, and the schema
    -- refuses to store one whatever path reaches it.
    CONSTRAINT user_follow_no_self CHECK (follower_id <> followee_id)
);

-- "Who follows this user" is the reverse of the primary key and just as frequent — the
-- followers list, and the mutual-follow check the next issue adds: it needs its own index.
CREATE INDEX idx_user_follow_followee ON user_follow (followee_id);
