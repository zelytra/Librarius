-- Private review of a series as a whole (#190), the series-level twin of the per-title
-- review V7 added to `library_item` (#48).
--
-- `library_item.rating`/`review` sit on an owned edition, so they have nothing to say about
-- a series: a reader with three volumes in the collection has three separate opinions there,
-- none of them about the run as a whole. This table gives the run its own opinion, exactly
-- as private as the title one -- returned to nobody but its author, and never aggregated
-- into anything the title review reports on. Sharing a series review with other members and
-- rolling reviews up into a public score are deliberately left to #205 and #206: this
-- migration and the API built on it only ever read and write the caller's own row.
--
-- A surrogate `id` rather than `(user_id, series_id)` as the primary key, unlike
-- `series_follow`: a follow is a flag with nothing else to carry, where a review is edited
-- and deleted as a row in its own right, which reads better against an identifier of its
-- own. `UNIQUE(user_id, series_id)` keeps the "one opinion per reader per series" rule the
-- follow table gets for free from its composite key.

CREATE TABLE series_review (
    id         UUID PRIMARY KEY,
    user_id    VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    series_id  UUID         NOT NULL REFERENCES series (id) ON DELETE CASCADE,
    rating     INT          NOT NULL,
    review     TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_series_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_series_review_user_series UNIQUE (user_id, series_id)
);

-- Reached whenever a series screen needs every review written on it -- not exposed by the
-- API yet (that visibility is #205), but the index is cheap to add now and expensive to
-- add later on a table already carrying data.
CREATE INDEX idx_series_review_series ON series_review (series_id);
