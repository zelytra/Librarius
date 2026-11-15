-- Ratings go by half stars (#484): 0.5 to 5, in steps of 0.5. A double holds every half
-- exactly; the checks keep anything in between out, on the reader's titles as on their series.
ALTER TABLE library_item ALTER COLUMN rating TYPE DOUBLE PRECISION;
-- library_item never had a check: a value the API would refuse is no rating, and goes.
UPDATE library_item SET rating = NULL WHERE rating NOT BETWEEN 1 AND 5;
ALTER TABLE library_item
    ADD CONSTRAINT ck_library_item_rating CHECK (rating BETWEEN 0.5 AND 5 AND rating * 2 = floor(rating * 2));

ALTER TABLE series_review DROP CONSTRAINT ck_series_review_rating;
ALTER TABLE series_review ALTER COLUMN rating TYPE DOUBLE PRECISION;
ALTER TABLE series_review
    ADD CONSTRAINT ck_series_review_rating CHECK (rating BETWEEN 0.5 AND 5 AND rating * 2 = floor(rating * 2));
