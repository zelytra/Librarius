-- Blocking another member (#203), building on the member follow (#200).
--
-- A block lets an account stop another from seeing its shared content and from interacting
-- with it. It is stored one-directionally — blocker → blocked, the account that pressed the
-- button and the account it was pressed on — but it takes effect BOTH ways: while alice
-- blocks bob, neither sees the other's shared reviews, activity or comments, whatever follow
-- or public-account setting stands between them. The symmetry lives in the predicate that
-- reads this table ("is there a block between A and B", either row present), not in the
-- storage: only alice knows she blocked bob, exactly as the specification asks.
--
-- A block overrides a follow. Existing user_follow rows are left as-is — no silent unfollow —
-- and the visibility gate simply stops granting while a block stands; a new follow attempt in
-- either direction is refused with 400 for as long as it does. Unblocking removes the row and
-- restores whatever the follow and public-account rules would otherwise say.
--
-- Same shape as user_follow (V18): no surrogate key, the (blocker, blocked) pair is the
-- identity and the primary key doubles as the "who this user blocks" index. Both foreign keys
-- are ON DELETE CASCADE (#73), so deleting an account removes the blocks it issued and the
-- blocks aimed at it in the same statement.

CREATE TABLE user_block (
    blocker_id VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    blocked_id VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    -- Nobody blocks themselves: the API refuses such a request with 400, and the schema
    -- refuses to store one whatever path reaches it.
    CONSTRAINT user_block_no_self CHECK (blocker_id <> blocked_id)
);

-- "Who blocked this user" is the reverse of the primary key: the block-between predicate
-- tests both directions, so the second lookup needs its own index.
CREATE INDEX idx_user_block_blocked ON user_block (blocked_id);
