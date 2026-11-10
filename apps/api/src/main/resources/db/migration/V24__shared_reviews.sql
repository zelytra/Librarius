-- Reviews a reader chooses to share (#205). Private stays the default, for every existing row
-- and every new one: shipping this changes nobody's exposure. A shared review is readable by
-- whoever can already see the author's activity (VisibilityGate), never by the whole internet.
ALTER TABLE library_item ADD COLUMN review_shared BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE series_review ADD COLUMN shared BOOLEAN NOT NULL DEFAULT false;

-- The aggregation reads the shared rows of one work or one series.
CREATE INDEX idx_library_item_review_shared ON library_item (edition_id) WHERE review_shared;
CREATE INDEX idx_series_review_shared ON series_review (series_id) WHERE shared;
