-- Makes the series a first-class object of the catalog.
--
-- Until now a series was nothing but the free-text `work.series_title`, deduplicated with
-- a `lower()` at read time. Counting the volumes of a series, following one, or spotting a
-- hole in a run were all impossible. This migration introduces the `series` table, links
-- the existing works to it, and adds the per-user follow.
--
-- `work.series_title` is deliberately kept and still populated: the front end reads it
-- through `BookView`, and dropping it here would break the deployed client. It becomes the
-- denormalised label of `series.title` and is dropped in a later migration, once the front
-- end reads the series identifier.

CREATE TABLE series (
    id             UUID PRIMARY KEY,
    kind           VARCHAR(16)  NOT NULL,          -- BOOK | MANGA
    title          VARCHAR(512) NOT NULL,
    original_title VARCHAR(512),
    total_volumes  INT,                            -- NULL when unknown or still running
    status         VARCHAR(16),                    -- ONGOING | COMPLETED | HIATUS
    cover_url      VARCHAR(1024),
    synopsis       TEXT,
    provider       VARCHAR(32),
    provider_ref   VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- A series is identified by its title within a kind, case-insensitively: that is the key
-- the import path uses to attach a new volume to an existing series, and the constraint
-- that stops "One Piece" and "one piece" from drifting apart into two runs.
CREATE UNIQUE INDEX uq_series_kind_title ON series (kind, lower(title));

ALTER TABLE work ADD COLUMN series_id UUID REFERENCES series (id) ON DELETE SET NULL;

-- Ordering the volumes of a series is the single most frequent read: the series screen,
-- the missing-volume detection and the upcoming releases all walk a series in volume order.
CREATE INDEX idx_work_series ON work (series_id, volume_number);

-- Per-user follow. No surrogate key: the pair is the identity, and the primary key doubles
-- as the index for "the series followed by this user".
CREATE TABLE series_follow (
    user_id    VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    series_id  UUID         NOT NULL REFERENCES series (id)   ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, series_id)
);

-- ── Data migration ────────────────────────────────────────────────────────────
-- One series per distinct (kind, series_title) already present in the catalog. The pairing
-- is case-insensitive and ignores surrounding whitespace, mirroring the deduplication the
-- statistics used to do in Java; `min()` picks one spelling deterministically so that two
-- runs of this migration on the same data produce the same titles.

INSERT INTO series (id, kind, title)
SELECT gen_random_uuid(), w.kind, min(trim(w.series_title))
FROM work w
WHERE w.series_title IS NOT NULL
  AND length(trim(w.series_title)) > 0
GROUP BY w.kind, lower(trim(w.series_title));

UPDATE work w
SET series_id = s.id
FROM series s
WHERE w.series_title IS NOT NULL
  AND length(trim(w.series_title)) > 0
  AND s.kind = w.kind
  AND lower(s.title) = lower(trim(w.series_title));
