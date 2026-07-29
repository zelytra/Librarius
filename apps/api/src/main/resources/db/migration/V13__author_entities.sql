-- Authors become rows of the catalog (#182).
--
-- `work.authors` is free text: a provider joins whatever names it returns with ", "
-- (`OpenLibraryProvider.toResult`, `BnfProvider.creators`) and the manual form takes a
-- sentence. Two novels by the same person share nothing but a string, so there is no author
-- to open a page on, nothing to follow, and no way to group a bibliography.
--
-- `author` holds the person, `work_author` credits them on a work, `author_follow` is the
-- per-user follow -- the same three shapes `series` / `work.series_id` / `series_follow`
-- (V4) and `genre` / `work_genre` (V6) already use.
--
-- `work.authors` is deliberately kept and still populated: `BookView` exposes it, the
-- deployed front end and both export formats read it, and `WorkRepository.findMatch`
-- deduplicates works on it. It becomes the denormalised label list of `work_author`, and is
-- dropped once the front end goes through the author identifiers (#199).
--
-- ── How much of the genre pattern this reuses, and how much it does not ───────
--
-- Reused: the split of a free-text list, and a fold of each part into a matching key. A
-- name is written a dozen ways -- "ISAAC ASIMOV", "Isaac  Asimov", "Isaac Asimov." -- and
-- without a fold each spelling founds an author of its own.
--
-- **Not reused: the alias table.** `genre_alias` works because the genres are a closed
-- vocabulary of a few dozen entries somebody can curate. Authors are not: the table would
-- have to hold the world's writers, and a wrong row does not mis-file a tag, it merges two
-- people's bibliographies. So this migration relates two spellings **only** when they fold
-- to the same key, and never by curated fiat.
--
-- What the fold therefore cannot do, stated plainly rather than implied away:
--
--   * "Damasio, Alain" splits on the comma into two authors. That is the accepted cost of
--     splitting on the character the providers join with -- the same trade-off V6 made.
--   * "A. Damasio" and "Alain Damasio" are two rows. Nothing expands an initial.
--   * "Neil Gaiman and Terry Pratchett" is one row: " and " / " et " are words, not
--     separators, and a rule cutting on them would also cut "Bell and Sons".
--   * Two different people sharing a name are one row, and there is no fixing that from a
--     name alone. `provider` / `provider_ref` below is where the eventual answer lands: a
--     catalogue identifier tells two namesakes apart, a spelling never will.

-- ── Normalisation ─────────────────────────────────────────────────────────────
-- Both functions are ported verbatim into `AuthorNormalizer`, which every entry written
-- after this migration goes through; `AuthorNormalizerSqlParityTest` runs the two over the
-- same names and compares, so the backfill below and the runtime cannot drift apart.

-- Splits a free-text credit list into the people it names.
--
-- Same separators as `genre_parts()` plus `&`, which credits a duo far more often than it
-- appears inside one person's name. A space is not a separator, and neither is a full stop:
-- "J. R. R. Tolkien" is one author.
CREATE FUNCTION author_parts(raw TEXT) RETURNS SETOF TEXT AS $$
    SELECT regexp_split_to_table(coalesce(raw, ''), E'[,;/|&\r\n]');
$$ LANGUAGE SQL IMMUTABLE;

-- Folds one name into the key two spellings of it have to share.
--
-- Same fold as `genre_code()` -- ligatures expanded, diacritics mapped onto ASCII through
-- an explicit `translate()` table, lower case, every other run of characters collapsed into
-- a single hyphen -- at the width of `author.name` rather than of `genre.code`. Collapsing
-- punctuation is what makes it worth having here: "J.R.R. Tolkien" and "J. R. R. Tolkien"
-- are the same person, and the catalogues disagree on the spacing of initials.
--
-- The table is longer than V6's, and deliberately: a genre list is French and English, an
-- author list is every language that has a writer. Without the Latin Extended-A rows,
-- "Stanisław Lem" and "Stanislaw Lem" would be two authors.
--
-- The fallback is the one real difference from `genre_code()`. A wording that folds to
-- nothing is dropped there, and rightly -- an unreadable genre is noise the statistics are
-- better off without. Here it is somebody's name in a script the table does not cover, and
-- dropping it would lose the only credit that work carries. So the trimmed name itself
-- becomes the key, verbatim: `lower()` on a script this table ignores is the database's
-- collation talking, which V6 went out of its way not to depend on. Two cases of one
-- Cyrillic name are therefore two rows -- a nicety no collation-independent rule can buy.
-- Such a key can collide with no folded key, those being `[a-z0-9-]` and never empty by
-- construction. Only a blank name yields nothing at all.
CREATE FUNCTION author_key(raw TEXT) RETURNS TEXT AS $$
    SELECT coalesce(
        nullif(
            -- The second trim catches a hyphen uncovered by the truncation.
            trim(BOTH '-' FROM left(
                trim(BOTH '-' FROM regexp_replace(
                    lower(translate(
                        replace(replace(replace(replace(replace(raw,
                            'Œ', 'oe'), 'œ', 'oe'), 'Æ', 'ae'), 'æ', 'ae'), 'ß', 'ss'),
                        'ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖØÙÚÛÜÝàáâãäåçèéêëìíîïñòóôõöøùúûüýÿŌōŪū'
                        || 'ĀāĂăĄąĆćČčĎďĐđĒēĖėĘęĚěĞğĪīĮįŁłĹĺĽľŃńŇňŐőŔŕŘřŚśŞşŠšŢţŤťŰűŲųŮůŴŵŶŷŸŹźŻżŽž',
                        'aaaaaaceeeeiiiinoooooouuuuyaaaaaaceeeeiiiinoooooouuuuyyoouu'
                        || 'aaaaaaccccddddeeeeeeeeggiiiillllllnnnnoorrrrssssssttttuuuuuuwwyyyzzzzzz')),
                    '[^a-z0-9]+', '-', 'g')),
                512)),                                 -- the width of author.name_key
            ''),
        nullif(left(trim(raw), 512), ''));
$$ LANGUAGE SQL IMMUTABLE STRICT;

-- ── Tables ────────────────────────────────────────────────────────────────────

-- A person credited on a work. Shared catalog data, like `work`, `edition` and `series`:
-- what belongs to a user is the follow below, nothing else.
--
-- `name_key` is the identity -- the fold of `name` -- and `name` only the spelling to show,
-- exactly the split `genre.code` / `genre.label` makes. No `kind`: a series is scoped by
-- kind because "One Piece" the manga and a novel of that name are two runs, whereas an
-- author who writes both novels and manga is one person.
CREATE TABLE author (
    id           UUID PRIMARY KEY,
    name         VARCHAR(512) NOT NULL,
    name_key     VARCHAR(512) NOT NULL,
    photo_url    VARCHAR(1024),
    provider     VARCHAR(32),
    provider_ref VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_author_name_key UNIQUE (name_key),
    -- The pair is one value, as on `work` and `edition` since V12.
    CONSTRAINT ck_author_provider_reference
        CHECK ((provider IS NULL) = (provider_ref IS NULL))
);

COMMENT ON COLUMN author.name_key IS
    'Fold of name (author_key) -- what makes two spellings the same person';
COMMENT ON COLUMN author.photo_url IS
    'Portrait, NULL until a provider supplies one -- free text names no picture';

-- The name search of #196 (`GET /api/authors?q=`) reads this one; `uq_author_name_key`
-- leads on the fold and answers nothing about a prefix of the spelling.
CREATE INDEX idx_author_name_lower ON author (lower(name));

-- Who is credited on what. Same shape as `work_genre`: a pure link, no surrogate key, and
-- no ordinal -- nothing needs a first author yet, and `work.authors` still carries the
-- credit line in the order it was written.
CREATE TABLE work_author (
    work_id   UUID NOT NULL REFERENCES work (id)   ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES author (id) ON DELETE CASCADE,
    PRIMARY KEY (work_id, author_id)
);

-- The primary key already serves "the authors of this work". The reverse -- the
-- bibliography, which is the whole point of #196 -- needs its own index.
CREATE INDEX idx_work_author_author ON work_author (author_id, work_id);

-- Per-user follow, byte for byte the shape of `series_follow`: no surrogate key, the pair
-- is the identity and its primary key doubles as the index for "the authors this user
-- follows".
CREATE TABLE author_follow (
    user_id    VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    author_id  UUID         NOT NULL REFERENCES author (id)   ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, author_id)
);

-- ── Data migration ────────────────────────────────────────────────────────────
-- Everything below is re-runnable: the two inserts are the exact statements
-- `AuthorBackfillTest` replays on a seeded database, twice, to prove that a second run
-- creates nothing. That is also what makes them safe next to the runtime, which resolves
-- the authors of every entry written after this migration the same way.

-- One author per distinct key found in the free-text credits. `min()` settles the spelling
-- so that two runs on the same data produce the same name -- which does mean an all-capital
-- credit can win where it sorts first. The name is a label, and unlike a genre it is not
-- normalised into a house style: "Ursula K. Le Guin" is how it is written, not
-- "Ursula k. le guin".
INSERT INTO author (id, name, name_key)
SELECT gen_random_uuid(), min(trim(part)), author_key(part)
FROM work w
CROSS JOIN LATERAL author_parts(w.authors) AS part
WHERE author_key(part) IS NOT NULL
GROUP BY author_key(part)
ON CONFLICT (name_key) DO NOTHING;

-- Then credit every work to the authors its free-text value names. `DISTINCT` because a
-- value reading "Asimov, asimov" names the same person twice.
INSERT INTO work_author (work_id, author_id)
SELECT DISTINCT w.id, a.id
FROM work w
CROSS JOIN LATERAL author_parts(w.authors) AS part
JOIN author a ON a.name_key = author_key(part)
ON CONFLICT DO NOTHING;
