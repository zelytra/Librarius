-- One category name per user.
--
-- POST /api/categories has always derived the code from the label and never looked at what
-- the user already had, so "Coup de coeur" could be created twice and produce two rows
-- sharing the code `coup-de-coeur`. The code is what GET /api/library?rank= filters on and
-- what LibraryItemDto reports, so a duplicate quietly turned one shelf into two the screen
-- could not tell apart. Now that the categories are managed from a screen, the constraint
-- belongs in the schema rather than in the caller's discipline.
--
-- The constraint covers (user_id, code). PostgreSQL considers two NULLs distinct, so it
-- never applies to the built-ins (`or`, `argent`, `bronze`, user_id NULL): those are seed
-- data, inserted once by V1 and never written again. What the constraint cannot express is
-- a custom code shadowing a built-in one — the two rows differ by user_id — so that rule is
-- enforced by CategoryService, which refuses a label whose code is already visible to the
-- user, built-ins included.

-- Existing rows are renamed, never deleted: a category carries the rank of every title
-- filed under it, and dropping the row would unrank them for the sake of a constraint.
-- The `~` separator cannot appear in a generated code (slugs are [a-z0-9-]), so a rewritten
-- code can collide neither with an existing code nor with another rewrite.

-- 1. A custom code shadowing a built-in one.
UPDATE rank_category rc
SET code = left(rc.code, 30) || '~0'
WHERE rc.user_id IS NOT NULL
  AND rc.code IN (SELECT code FROM rank_category WHERE user_id IS NULL);

-- 2. The same code created twice by the same user — the oldest keeps it.
WITH duplicate AS (
    SELECT id,
           code,
           row_number() OVER (PARTITION BY user_id, code ORDER BY sort_order, id) AS occurrence
    FROM rank_category
    WHERE user_id IS NOT NULL
)
UPDATE rank_category rc
SET code = left(d.code, 32 - length('~' || d.occurrence)) || '~' || d.occurrence
FROM duplicate d
WHERE rc.id = d.id
  AND d.occurrence > 1;

ALTER TABLE rank_category
    ADD CONSTRAINT uq_rank_category_user_code UNIQUE (user_id, code);

-- The constraint's index leads on user_id, which is exactly what idx_rank_category_user was
-- there for.
DROP INDEX idx_rank_category_user;
