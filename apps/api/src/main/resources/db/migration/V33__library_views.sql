-- Library views (#451) and the audiobook ownership (#455).

-- A view is a way of laying out the library the reader saved under a name: which kind and
-- which shelf it opens on, how it is sorted (`manual` included), whether series are drawn as
-- one block or volume by volume, and whether it is split into one section per category.
CREATE TABLE library_view (
    id                UUID PRIMARY KEY,
    user_id           VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name              VARCHAR(60)  NOT NULL,
    position          INT          NOT NULL DEFAULT 0,
    kind              VARCHAR(16),
    shelf             VARCHAR(80),
    sort              VARCHAR(16)  NOT NULL DEFAULT 'recent',
    layout            VARCHAR(16)  NOT NULL DEFAULT 'grouped',
    group_by_category BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_library_view_layout CHECK (layout IN ('grouped', 'flat'))
);

CREATE INDEX idx_library_view_user ON library_view (user_id, position);

-- The order the reader dragged the entries of a view into: one row per title or per series
-- block, by position. An entry the reader never placed has no row and follows the placed
-- ones; a title or a series that goes away takes its row with it.
CREATE TABLE library_view_entry (
    id        UUID PRIMARY KEY,
    view_id   UUID NOT NULL REFERENCES library_view (id) ON DELETE CASCADE,
    position  INT  NOT NULL,
    item_id   UUID REFERENCES library_item (id) ON DELETE CASCADE,
    series_id UUID REFERENCES series (id) ON DELETE CASCADE,

    CONSTRAINT ck_library_view_entry_target CHECK ((item_id IS NULL) <> (series_id IS NULL)),
    CONSTRAINT uq_library_view_entry_item UNIQUE (view_id, item_id),
    CONSTRAINT uq_library_view_entry_series UNIQUE (view_id, series_id)
);

CREATE INDEX idx_library_view_entry_view ON library_view_entry (view_id, position);

COMMENT ON COLUMN library_item.ownership IS
    'OWNED | BORROWED | EBOOK | AUDIOBOOK | WISHLIST | NOT_OWNED (zelytra.librarius.domain.Ownership)';
