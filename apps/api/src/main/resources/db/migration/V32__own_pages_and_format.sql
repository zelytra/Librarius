-- The reader's own page count and format of their copy (#446, #447).

-- A page count the reader typed for their copy, private to their row like `price` (V30). Every
-- figure of that reader prefers it to the edition's count and to its estimate (V28).
ALTER TABLE library_item
    ADD COLUMN page_count INTEGER CHECK (page_count IS NULL OR page_count > 0);

-- The format of the reader's copy, picked like the reading status and the ownership:
-- POCKET | PAPERBACK | HARDCOVER | LARGE | DELUXE | BOX_SET (zelytra.librarius.domain.BookFormat).
-- NULL when the reader said nothing.
ALTER TABLE library_item
    ADD COLUMN format VARCHAR(16);
