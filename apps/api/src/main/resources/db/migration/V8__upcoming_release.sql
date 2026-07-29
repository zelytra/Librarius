-- Announced releases of a series, so that "what is coming" stops meaning "what AniList
-- happens to be trending".
--
-- `GET /api/catalog/upcoming` asks the providers on every display and answers the same
-- global list to everybody, with dates that belong to the original edition. This table
-- holds the announcements themselves, per series and per market, so that a screen reads
-- rows instead of spending the Open Library / AniList quota the whole instance shares.
--
-- It is **catalog** data, like `series`, `work` and `edition`: it says what is coming out,
-- never who is waiting for it. What makes the list personal is the join done at read time
-- against the caller's own collection, wishlist and follows — no column here is
-- user-scoped, and none must ever become one.
--
-- A release date is a fragile thing: often missing, often approximate, sometimes regional.
-- Three columns carry that honestly rather than pretending to a precision the data does
-- not have:
--
--   * `release_date` is nullable — a volume can be announced with no date at all;
--   * `date_precision` says how much of that date is real (DAY, MONTH, QUARTER, YEAR), so
--     a client renders "mars 2027" where it only knows the month, instead of inventing the
--     1st of the month it stored;
--   * `confidence` separates a date the publisher announced from one deduced from a
--     publication rhythm.
--
-- The table starts **empty**: nothing in the schema so far carries an announced future
-- date together with the market it applies to. It is filled by `UpcomingReleaseRefresher`
-- and by curated rows entered by hand, which the refresher never overwrites (see
-- `source` below).

CREATE TABLE upcoming_release (
    id             UUID PRIMARY KEY,
    series_id      UUID         NOT NULL REFERENCES series (id) ON DELETE CASCADE,
    -- NULL when the announcement names no volume: a provider that dates a series start
    -- rather than a tome, or a one-shot.
    volume_number  INT,
    title          VARCHAR(512),
    release_date   DATE,
    -- Granularity of `release_date`, which is always stored on the first day of the window
    -- it opens: DAY | MONTH | QUARTER | YEAR.
    date_precision VARCHAR(8),
    -- Market the date applies to: FR = French edition, JP = original edition,
    -- EN = English edition. This is what the interface must state unambiguously — a JP
    -- date shown without it is the very confusion this table exists to remove.
    region         VARCHAR(8)   NOT NULL,
    publisher      VARCHAR(255),
    -- Where the row came from: `manual` for a curated entry, `catalog` for a date read off
    -- an edition we already hold, or the provider name. `manual` wins over everything: the
    -- refresher leaves those rows alone, since a hand-checked French date beats a guess.
    source         VARCHAR(32)  NOT NULL,
    confidence     VARCHAR(16)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- A precision without a date describes nothing, and a date without a precision cannot
    -- be rendered without guessing: the two travel together or not at all.
    CONSTRAINT ck_upcoming_release_dated
        CHECK ((release_date IS NULL) = (date_precision IS NULL)),
    CONSTRAINT ck_upcoming_release_precision
        CHECK (date_precision IS NULL
               OR date_precision IN ('DAY', 'MONTH', 'QUARTER', 'YEAR')),
    CONSTRAINT ck_upcoming_release_region
        CHECK (region IN ('FR', 'JP', 'EN')),
    CONSTRAINT ck_upcoming_release_confidence
        CHECK (confidence IN ('CONFIRMED', 'ESTIMATED'))
);

-- One announcement per volume and per market: the French edition of tome 12 and its
-- original edition are two rows, two dates and two labels, and re-running the refresh
-- updates a row rather than piling up duplicates. `coalesce` because an announcement
-- naming no volume must collide with itself, where NULL never equals NULL.
CREATE UNIQUE INDEX uq_upcoming_release_volume
    ON upcoming_release (series_id, (coalesce(volume_number, -1)), region);

-- The read walks the series the caller has a stake in, keeps what is still ahead, and
-- orders by date. Leading with the series identifier is what turns that into an index
-- scan over a handful of runs rather than over every announcement stored.
CREATE INDEX idx_upcoming_release_series_date
    ON upcoming_release (series_id, release_date);
