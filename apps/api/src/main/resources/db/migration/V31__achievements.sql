-- Achievements, XP and levels (#287).
--
-- The catalogue of achievements lives in code (zelytra.librarius.achievement.Achievement):
-- only the unlocks are stored, one row per achievement and tier, so an unlock stays earned
-- even when the data that earned it changes later — a book given away does not take a badge
-- back. XP and the level are derived from these rows and from the books read, never stored.
CREATE TABLE user_achievement (
    user_id     VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    code        VARCHAR(64)  NOT NULL,
    tier        SMALLINT     NOT NULL,
    unlocked_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- When the reader was shown it; NULL until then, which is what the unlock toast reads.
    seen_at     TIMESTAMPTZ,
    PRIMARY KEY (user_id, code, tier)
);

CREATE INDEX idx_user_achievement_unseen ON user_achievement (user_id) WHERE seen_at IS NULL;

-- The first evaluation of an account is retroactive and silent: everything already earned is
-- unlocked as seen, rather than greeting a long-time reader with forty toasts.
ALTER TABLE app_user
    ADD COLUMN achievements_evaluated_at TIMESTAMPTZ;

-- Whether a title was ever given up on, so that reading it to the end later — through a
-- resumed reading or straight to READ — unlocks COMEBACK for that very title.
ALTER TABLE library_item
    ADD COLUMN was_abandoned BOOLEAN NOT NULL DEFAULT false;

UPDATE library_item SET was_abandoned = true WHERE status = 'ABANDONED';
