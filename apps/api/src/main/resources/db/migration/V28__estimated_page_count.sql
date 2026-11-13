-- Estimated page counts (#414).
--
-- An edition no catalog knew the length of carried a NULL page count, which left holes in
-- the pages read, the stack on Home and the progress percentage. Such an edition now gets
-- an estimate in a column of its own: `page_count` stays what a catalog or the reader said,
-- so a real count found later simply takes precedence, and nothing that tells a bare
-- edition from a described one (the work match, the enricher) mistakes a guess for data.
-- Every aggregation reads coalesce(page_count, page_count_estimate).
ALTER TABLE edition
    ADD COLUMN page_count_estimate INTEGER;

-- A default per kind first, the figures zelytra.librarius.library.PageCountEstimator holds.
UPDATE edition e
SET page_count_estimate = CASE w.kind
                              WHEN 'MANGA' THEN 192
                              WHEN 'COMIC' THEN 56
                              WHEN 'GRAPHIC_NOVEL' THEN 160
                              ELSE 320
                          END
FROM work w
WHERE e.work_id = w.id
  AND e.page_count IS NULL;

-- Then the average of the known volumes of the same series, where there is one: a manga
-- series is far more regular than any per-kind figure.
WITH series_average AS (
    SELECT w.series_id, round(avg(e.page_count))::int AS pages
    FROM edition e
      JOIN work w ON w.id = e.work_id
    WHERE w.series_id IS NOT NULL
      AND e.page_count IS NOT NULL
      AND e.page_count > 0
    GROUP BY w.series_id
)
UPDATE edition e
SET page_count_estimate = sa.pages
FROM work w
  JOIN series_average sa ON sa.series_id = w.series_id
WHERE e.work_id = w.id
  AND e.page_count IS NULL;
