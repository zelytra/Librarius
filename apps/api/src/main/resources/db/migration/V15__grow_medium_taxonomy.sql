-- Grow the medium taxonomy past books and manga (#178).
--
-- `zelytra.librarius.domain.Kind` gains COMIC, GRAPHIC_NOVEL and AUDIOBOOK next to BOOK and
-- MANGA, the foundation the rest of v0.8 (generic search, Discover) builds on. The values
-- live in `work.kind` and `series.kind`, both `VARCHAR(16) NOT NULL` since V1 / V4 with no
-- CHECK constraint and every new name inside the width -- so there is nothing to ALTER: the
-- column already accepts them, and existing BOOK / MANGA rows are untouched.
--
-- What is left is the documentation. V1 and V4 label the columns `-- BOOK | MANGA` in a
-- comment, and those files have shipped: the Flyway checksum covers the whole file, so their
-- comments cannot be reworded without failing validation where the migration already ran.
-- The full list is recorded here instead, as a real column comment `\d+` and every schema
-- tool can read -- and, unlike a `--` line buried in V1, one that sits on the live column.

COMMENT ON COLUMN work.kind IS
    'Medium of the work: BOOK | MANGA | COMIC | GRAPHIC_NOVEL | AUDIOBOOK (domain.Kind)';

COMMENT ON COLUMN series.kind IS
    'Medium of the series: BOOK | MANGA | COMIC | GRAPHIC_NOVEL | AUDIOBOOK (domain.Kind)';
