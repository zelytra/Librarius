-- Private review of an owned title.
--
-- `library_item.rating` has existed since V1 but nothing ever wrote to it, and there was
-- nowhere to keep what the reader actually thought of a title. The review lives on
-- `library_item` rather than on the shared catalog on purpose: an opinion belongs to one
-- user's copy of a book, it is never shared and never aggregated across accounts. Putting
-- it on `work` would have made one reader's notes visible to everyone owning the title.
--
-- TEXT rather than a bounded VARCHAR: a review has no natural length, and PostgreSQL
-- stores both the same way.

ALTER TABLE library_item
    ADD COLUMN review TEXT;

-- Backs the "my favourites" filter (rating >= 4) and the ordering by rating, both of which
-- narrow a set already scoped to one user. NULLS LAST matches the ordering the API applies:
-- unrated titles come after the rated ones rather than ahead of them.
CREATE INDEX idx_library_item_rating ON library_item (user_id, rating DESC NULLS LAST);
