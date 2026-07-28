-- Normalised genres.
--
-- `work.genres` is free text treated as one atomic value: a provider returning
-- "Fantasy, Aventure" produced a value the statistics counted as a genre of its own,
-- distinct from "Fantasy". The breakdown was therefore wrong as soon as a title carried more
-- than one genre — which both providers routinely return — and no genre filter could be
-- built on top of it.
--
-- Genres become rows: `genre` holds the canonical list keyed by a code, `genre_alias` maps
-- the wordings the providers use onto those codes, and `work_genre` attaches them to works.
--
-- `work.genres` is deliberately kept and still populated: `BookView` exposes it and the
-- deployed front end reads it. It becomes the denormalised label list of `work_genre`, and
-- is dropped once the front end reads the codes.
--
-- ── What "the same genre" means ───────────────────────────────────────────────
--
-- Two wordings designate the same genre when they produce the same code. `genre_code()`
-- builds it: ligatures expanded, accents and macrons folded onto ASCII, lower case, and
-- every run of anything else collapsed into a single hyphen. "Science-Fiction",
-- "science fiction" and "SCIENCE FICTION" all become `science-fiction`; "Poésie" becomes
-- `poesie`; "Shōnen" becomes `shonen`.
--
-- Wordings that differ by more than spelling — another language, a plural, an abbreviation,
-- a publishing category — are related through `genre_alias`, seeded below with what Open
-- Library and AniList actually return. The table is deliberately short: it covers the
-- wordings seen in practice, not the whole of BISAC.
--
-- A wording holding no ASCII letter or digit once folded (blank, punctuation only, a script
-- the fold does not cover) yields no code at all and is dropped, rather than stored under a
-- meaningless one.

-- ── Tables ────────────────────────────────────────────────────────────────────

-- The canonical genre. `code` is the identity — stable, safe in a URL, and what the API
-- filters on; `label` is what a screen shows and carries no meaning of its own.
CREATE TABLE genre (
    id    UUID PRIMARY KEY,
    code  VARCHAR(64) NOT NULL,
    label VARCHAR(64) NOT NULL,
    CONSTRAINT uq_genre_code UNIQUE (code)
);

-- Provider wording -> canonical code. `alias` is itself a code, i.e. the output of
-- `genre_code()` on the raw wording, so a lookup is one equality on a primary key.
CREATE TABLE genre_alias (
    alias VARCHAR(64) PRIMARY KEY,
    code  VARCHAR(64) NOT NULL REFERENCES genre (code) ON DELETE CASCADE
);

CREATE TABLE work_genre (
    work_id  UUID NOT NULL REFERENCES work (id)  ON DELETE CASCADE,
    genre_id UUID NOT NULL REFERENCES genre (id) ON DELETE CASCADE,
    PRIMARY KEY (work_id, genre_id)
);

-- The primary key already serves "the genres of this work". The reverse — "the works of
-- this genre", which the collection filter and the statistics walk — needs its own index.
CREATE INDEX idx_work_genre_genre ON work_genre (genre_id, work_id);

-- ── Normalisation ─────────────────────────────────────────────────────────────
-- Both functions are ported verbatim into `GenreNormalizer` on the Java side, which applies
-- them to every genre written after this migration. `GenreNormalizerSqlParityTest` calls
-- these two and compares, so the backfill below and the runtime cannot drift apart.

-- Splits a free-text genre list into its parts. The separators are those the providers and
-- the manual form actually use; a space is not one of them, "Science fiction" being a single
-- genre and not two.
CREATE FUNCTION genre_parts(raw TEXT) RETURNS SETOF TEXT AS $$
    SELECT regexp_split_to_table(coalesce(raw, ''), E'[,;/|\r\n]');
$$ LANGUAGE SQL IMMUTABLE;

-- Folds one wording into its code, or NULL when nothing usable is left.
--
-- `unaccent` would read better than `translate`, but it lives in an extension the API role
-- may not be allowed to install — the same reason V3 does without `pg_trgm`. The explicit
-- table also makes the fold independent of the database collation: `lower()` alone leaves
-- accented letters untouched under a C locale, which would let "Poésie" and "POÉSIE" drift
-- into two codes.
CREATE FUNCTION genre_code(raw TEXT) RETURNS TEXT AS $$
    SELECT nullif(
        -- The second trim catches a hyphen uncovered by the truncation.
        trim(BOTH '-' FROM left(
            trim(BOTH '-' FROM regexp_replace(
                lower(translate(
                    replace(replace(replace(replace(raw,
                        'Œ', 'oe'), 'œ', 'oe'), 'Æ', 'ae'), 'æ', 'ae'),
                    'ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖØÙÚÛÜÝàáâãäåçèéêëìíîïñòóôõöøùúûüýÿŌōŪū',
                    'aaaaaaceeeeiiiinoooooouuuuyaaaaaaceeeeiiiinoooooouuuuyyoouu')),
                '[^a-z0-9]+', '-', 'g')),
            64)),                                  -- the width of genre.code
        '');
$$ LANGUAGE SQL IMMUTABLE STRICT;

-- ── Canonical genres ──────────────────────────────────────────────────────────
-- The genres worth naming once and for all, with the French label the interface shows.
-- Anything else is created from the wording it was first seen under: this list is a
-- starting point, not a closed vocabulary.

INSERT INTO genre (id, code, label) VALUES
    (gen_random_uuid(), 'action',          'Action'),
    (gen_random_uuid(), 'aventure',        'Aventure'),
    (gen_random_uuid(), 'bande-dessinee',  'Bande dessinée'),
    (gen_random_uuid(), 'biographie',      'Biographie'),
    (gen_random_uuid(), 'comedie',         'Comédie'),
    (gen_random_uuid(), 'documentaire',    'Documentaire'),
    (gen_random_uuid(), 'drame',           'Drame'),
    (gen_random_uuid(), 'essai',           'Essai'),
    (gen_random_uuid(), 'fantastique',     'Fantastique'),
    (gen_random_uuid(), 'fantasy',         'Fantasy'),
    (gen_random_uuid(), 'fiction',         'Fiction'),
    (gen_random_uuid(), 'historique',      'Historique'),
    (gen_random_uuid(), 'horreur',         'Horreur'),
    (gen_random_uuid(), 'jeunesse',        'Jeunesse'),
    (gen_random_uuid(), 'josei',           'Josei'),
    (gen_random_uuid(), 'manga',           'Manga'),
    (gen_random_uuid(), 'poesie',          'Poésie'),
    (gen_random_uuid(), 'policier',        'Policier'),
    (gen_random_uuid(), 'psychologique',   'Psychologique'),
    (gen_random_uuid(), 'romance',         'Romance'),
    (gen_random_uuid(), 'science-fiction', 'Science-fiction'),
    (gen_random_uuid(), 'seinen',          'Seinen'),
    (gen_random_uuid(), 'shojo',           'Shojo'),
    (gen_random_uuid(), 'shonen',          'Shonen'),
    (gen_random_uuid(), 'thriller',        'Thriller'),
    (gen_random_uuid(), 'tranche-de-vie',  'Tranche de vie')
ON CONFLICT (code) DO NOTHING;

-- ── Provider wordings ─────────────────────────────────────────────────────────
-- Left column: what `genre_code()` produces from the provider's own wording. Open Library
-- returns library subjects ("Juvenile fiction", "Detective and mystery stories", the BISAC
-- headings), AniList returns its English tag list ("Shounen", "Slice of Life").

INSERT INTO genre_alias (alias, code) VALUES
    -- Science fiction
    ('sf',                            'science-fiction'),
    ('sci-fi',                        'science-fiction'),
    ('scifi',                         'science-fiction'),
    ('anticipation',                  'science-fiction'),
    -- Fantasy and the fantastic
    ('heroic-fantasy',                'fantasy'),
    ('high-fantasy',                  'fantasy'),
    ('epic-fantasy',                  'fantasy'),
    ('fantasy-fiction',               'fantasy'),
    ('supernatural',                  'fantastique'),
    ('surnaturel',                    'fantastique'),
    ('paranormal',                    'fantastique'),
    -- Crime and suspense
    ('polar',                         'policier'),
    ('crime',                         'policier'),
    ('crime-fiction',                 'policier'),
    ('mystery',                       'policier'),
    ('mystere',                       'policier'),
    ('detective-and-mystery-stories', 'policier'),
    ('roman-policier',                'policier'),
    ('suspense',                      'thriller'),
    ('horror',                        'horreur'),
    -- Romance and adventure
    ('love-stories',                  'romance'),
    ('romance-fiction',               'romance'),
    ('sentimental',                   'romance'),
    ('adventure',                     'aventure'),
    ('adventure-and-adventurers',     'aventure'),
    ('action-adventure',              'aventure'),
    -- History, biography, non-fiction
    ('historical',                    'historique'),
    ('historical-fiction',            'historique'),
    ('history',                       'historique'),
    ('histoire',                      'historique'),
    ('biography',                     'biographie'),
    ('biography-autobiography',       'biographie'),
    ('autobiography',                 'biographie'),
    ('autobiographie',                'biographie'),
    ('memoir',                        'biographie'),
    ('poetry',                        'poesie'),
    ('essay',                         'essai'),
    ('essays',                        'essai'),
    ('non-fiction',                   'documentaire'),
    ('nonfiction',                    'documentaire'),
    ('documentary',                   'documentaire'),
    -- Young readers
    ('juvenile-fiction',              'jeunesse'),
    ('juvenile-literature',           'jeunesse'),
    ('children-s-fiction',            'jeunesse'),
    ('young-adult',                   'jeunesse'),
    ('young-adult-fiction',           'jeunesse'),
    ('litterature-jeunesse',          'jeunesse'),
    -- Tone
    ('comedy',                        'comedie'),
    ('humour',                        'comedie'),
    ('humor',                         'comedie'),
    ('humoristique',                  'comedie'),
    ('drama',                         'drame'),
    ('slice-of-life',                 'tranche-de-vie'),
    ('psychological',                 'psychologique'),
    -- Comics and manga demographics
    ('comics',                        'bande-dessinee'),
    ('bd',                            'bande-dessinee'),
    ('bandes-dessinees',              'bande-dessinee'),
    ('comics-graphic-novels',         'bande-dessinee'),
    ('graphic-novel',                 'bande-dessinee'),
    ('graphic-novels',                'bande-dessinee'),
    ('mangas',                        'manga'),
    ('shounen',                       'shonen'),
    ('shoujo',                        'shojo'),
    -- "Roman" is a format, not a genre; it lands where a bare "Fiction" does.
    ('roman',                         'fiction'),
    ('romans',                        'fiction')
ON CONFLICT (alias) DO NOTHING;

-- ── Data migration ────────────────────────────────────────────────────────────
-- Everything below is re-runnable: the two inserts are the exact statements
-- `GenreBackfillTest` replays on a seeded database, twice, to prove that a second run
-- creates nothing. That is also what makes them safe next to the runtime, which resolves
-- genres the same way for every work written after this migration.

-- One genre per distinct code found in the free-text values. Codes already seeded above keep
-- their curated label; the others take the wording they were seen under, normalised to
-- "first letter upper, rest lower" and settled with `min()` so that two runs on the same
-- data produce the same label.
INSERT INTO genre (id, code, label)
SELECT gen_random_uuid(), resolved.code, min(resolved.label)
FROM (
    SELECT coalesce(a.code, genre_code(part)) AS code,
           left(upper(left(lower(trim(part)), 1)) || substr(lower(trim(part)), 2), 64) AS label
    FROM work w
    CROSS JOIN LATERAL genre_parts(w.genres) AS part
    LEFT JOIN genre_alias a ON a.alias = genre_code(part)
    WHERE genre_code(part) IS NOT NULL
) AS resolved
GROUP BY resolved.code
ON CONFLICT (code) DO NOTHING;

-- Then attach every work to the genres its free-text value named. `DISTINCT` because a work
-- tagged "Fantasy, fantasy" names the same genre twice.
INSERT INTO work_genre (work_id, genre_id)
SELECT DISTINCT w.id, g.id
FROM work w
CROSS JOIN LATERAL genre_parts(w.genres) AS part
LEFT JOIN genre_alias a ON a.alias = genre_code(part)
JOIN genre g ON g.code = coalesce(a.code, genre_code(part))
WHERE genre_code(part) IS NOT NULL
ON CONFLICT DO NOTHING;
