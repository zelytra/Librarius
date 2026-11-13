-- Series totals that keep being looked up (#429), and a price of the reader's own (#431).

-- When the volume count of a series was last asked of the providers, found or not. Without it
-- the refresher picked the same series still missing a total on every run, so a handful that
-- no provider knows held back every other one for ever.
ALTER TABLE series
    ADD COLUMN volumes_checked_at TIMESTAMPTZ;

-- What the reader paid, or reckons their copy is worth. Private to that reader's title: the
-- shared edition.list_price (V29) is never touched by it. The library value counts it first.
ALTER TABLE library_item
    ADD COLUMN price NUMERIC(8, 2);
