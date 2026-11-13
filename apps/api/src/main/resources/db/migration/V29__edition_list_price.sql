-- The publisher's price of an edition (#421).
--
-- Read from the BnF record (UNIMARC 010$d, e.g. "14,95 EUR"). Under the French fixed book
-- price law that price is the retail price of a new copy anywhere in France, which is what
-- the library value and the wishlist's market price are computed from.
ALTER TABLE edition
    ADD COLUMN list_price NUMERIC(8, 2),
    ADD COLUMN list_price_currency VARCHAR(3),
    ADD COLUMN price_checked_at TIMESTAMPTZ;
