-- Two status families for a title (#411).
--
-- `library_item.status` used to mix two questions: OWNED meant both "I have the book" and
-- "I have not started it", and READING / READ / ABANDONED implied that the book was owned.
-- The reading status keeps the column; whether the reader has the book moves to a column
-- of its own.

-- 1. The reading status: OWNED was the to-read pile, and is now named so.
UPDATE library_item SET status = 'TO_READ' WHERE status = 'OWNED';

COMMENT ON COLUMN library_item.status IS
    'TO_READ | READING | READ | ABANDONED (zelytra.librarius.domain.LibraryStatus)';

-- 2. The ownership. Every existing row was an owned title, which is what the default says.
ALTER TABLE library_item
    ADD COLUMN ownership VARCHAR(16) NOT NULL DEFAULT 'OWNED';

COMMENT ON COLUMN library_item.ownership IS
    'OWNED | BORROWED | EBOOK | WISHLIST | NOT_OWNED (zelytra.librarius.domain.Ownership)';

CREATE INDEX idx_library_user_ownership ON library_item (user_id, ownership);
