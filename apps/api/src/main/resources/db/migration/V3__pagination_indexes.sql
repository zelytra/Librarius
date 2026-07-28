-- Indexes backing the server-side pagination of /api/library and /api/wishlist.
--
-- Both endpoints always filter on user_id first, then sort. The existing composite
-- indexes cover the (user_id, status) and (user_id, priority) filters; what was missing
-- is the default ordering — most recent first — and the orderings that reach into the
-- joined work (title, authors, genres), which the collection screen offers.
--
-- No trigram index for the free-text search: it runs on a set already narrowed down to
-- one user's items, so a sequential scan over that subset is cheap. Should a library
-- grow large enough for that to hurt, pg_trgm becomes a migration of its own rather
-- than an extension installed here, where it would need privileges the API role may
-- not have.

-- Default ordering of the collection and of the wishlist: newest first, per user.
CREATE INDEX idx_library_item_user_created ON library_item (user_id, created_at DESC);
CREATE INDEX idx_wishlist_item_user_created ON wishlist_item (user_id, created_at DESC);

-- The `kind` filter (books / mangas shelf) is carried by the work, not by the item.
CREATE INDEX idx_work_kind ON work (kind);

-- Orderings by title, author and genre are case-insensitive, hence the expression
-- indexes: a plain btree on the column would not be usable by `order by lower(...)`.
CREATE INDEX idx_work_title_lower ON work (lower(title));
CREATE INDEX idx_work_authors_lower ON work (lower(authors));
CREATE INDEX idx_work_genres_lower ON work (lower(genres));
