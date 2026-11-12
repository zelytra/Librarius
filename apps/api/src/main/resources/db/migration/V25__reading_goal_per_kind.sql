-- A reading goal per kind of book — roman, manga, BD — instead of one over everything (#393).
-- The goals already set become book goals: the column defaults to BOOK.
ALTER TABLE reading_goal ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'BOOK';
ALTER TABLE reading_goal DROP CONSTRAINT uq_goal_user_year;
ALTER TABLE reading_goal ADD CONSTRAINT uq_goal_user_year_kind UNIQUE (user_id, year, kind);
