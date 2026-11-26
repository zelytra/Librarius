-- #646: a title carries at most one rank — a built-in shelf (Platine, Or, Argent, Bronze,
-- Abandon), still `library_item.rank_category_id` — and any number of the reader's own
-- categories ("Or + Polar"), which move to this join table.

CREATE TABLE library_item_category (
    library_item_id UUID NOT NULL REFERENCES library_item (id) ON DELETE CASCADE,
    category_id     UUID NOT NULL REFERENCES rank_category (id) ON DELETE CASCADE,
    CONSTRAINT pk_library_item_category PRIMARY KEY (library_item_id, category_id)
);

-- "The titles filed under this category", for the collection filter and the Categories screen.
CREATE INDEX idx_library_item_category_category ON library_item_category (category_id);

-- A custom category held in the single column moves to the join table, leaving the column to
-- the built-in rank alone.
INSERT INTO library_item_category (library_item_id, category_id)
SELECT li.id, li.rank_category_id
FROM library_item li
JOIN rank_category rc ON rc.id = li.rank_category_id
WHERE NOT rc.is_builtin;

UPDATE library_item li
SET rank_category_id = NULL
FROM rank_category rc
WHERE rc.id = li.rank_category_id AND NOT rc.is_builtin;
