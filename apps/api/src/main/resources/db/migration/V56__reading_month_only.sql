-- #651: Booknode's reading history names the month a book was read, never the day. Such a finish
-- is stored on the first of the month, flagged here, so the screens say « lu en novembre 2026 »
-- rather than a day nobody gave. A date the reader enters is a day again.
ALTER TABLE reading_progress ADD COLUMN finished_month_only BOOLEAN NOT NULL DEFAULT false;
