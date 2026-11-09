-- What an import left incomplete, and what the enricher needs to stop retrying forever.

-- 1. The last time the import enricher looked this edition up in the catalog. The enricher
--    used to pick the newest editions still missing a page count on every run, so 25 titles
--    the catalog does not know held the head of the queue for good and the rest of a large
--    import was never looked at. An attempt now moves an edition to the back, and it is only
--    retried once the cooldown has passed.
ALTER TABLE edition ADD COLUMN enrich_attempted_at TIMESTAMPTZ;

-- 2. A title marked read with no reading progress: what every import produced before it
--    started recording the reading. Its Detail screen read "not started" next to a "read"
--    status. The reading is complete; its date is unknown and is not invented.
INSERT INTO reading_progress (id, library_item_id, percent, current_page)
SELECT gen_random_uuid(), li.id, 100, e.page_count
FROM library_item li
JOIN edition e ON e.id = li.edition_id
WHERE li.status = 'READ'
  AND NOT EXISTS (SELECT 1 FROM reading_progress rp WHERE rp.library_item_id = li.id);
