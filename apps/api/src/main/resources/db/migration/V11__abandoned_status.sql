-- Fourth reading status: a title the reader gave up on (#163).

-- No structural change is needed. `library_item.status` is a bare VARCHAR(16) holding the
-- name of the enum, with neither a CHECK constraint nor a database enum type behind it, so
-- 'ABANDONED' is already storable and `idx_library_user_status` already indexes it. What
-- V1 does carry is an inline comment enumerating the three values it knew about, and that
-- comment cannot be corrected: Flyway checksums the whole file, comments included, and the
-- migration has already run everywhere. The column comment below says it where a reader of
-- the live schema actually looks.
COMMENT ON COLUMN library_item.status IS
    'OWNED | READING | READ | ABANDONED (zelytra.librarius.domain.LibraryStatus)';

-- The shelf an abandoned title is filed under, alongside the Or / Argent / Bronze of V1.
-- It has to exist before the post-abandon rating screen (#165) can pre-select it, and it
-- is a built-in rather than a per-user row for the same reason the three others are: one
-- shared category (user_id NULL) that every account sees, that CategoryService refuses to
-- rename or delete, and that `/api/library?rank=abandon` turns into a shelf of its own.
--
-- The label is French like the three others -- it is displayed as-is, and the interface is
-- French (CONVENTIONS section 1). The identifier continues the series V1 opened, which
-- also makes the statement re-runnable: UNIQUE(user_id, code) from V9 never fires on these
-- rows, PostgreSQL treating NULLs as distinct, so the primary key is what guards it.
INSERT INTO rank_category (id, user_id, code, label, color, sort_order, is_builtin) VALUES
    ('00000000-0000-0000-0000-0000000000a4', NULL, 'abandon', 'Abandon', '#8f8579', 4, true)
ON CONFLICT (id) DO NOTHING;
