-- Where a work comes from, so a provider can be asked about it again (#184).
--
-- `edition` has carried `provider` / `provider_ref` since V1 and `series` since V4, but
-- `work` never did -- and the work is the level the question is asked at: "the other
-- editions of *this* title" is a question about the work, not about the copy the user
-- happens to own. Without it, `GET /api/works/{id}/editions` can only ever list what users
-- entered by hand (API gap A12), and #197 has nothing to enrich from.
--
-- The two columns are one value: a provider name with no reference resolves to nothing,
-- and a reference with no provider names nothing. The CHECK below says so, the same way
-- `ck_upcoming_release_dated` (V8) ties a date to its precision -- so that a reader can
-- take `provider IS NOT NULL` to mean "there is a record to ask about" without having to
-- re-test the other column.
ALTER TABLE work
    ADD COLUMN provider     VARCHAR(32),
    ADD COLUMN provider_ref VARCHAR(255),
    ADD CONSTRAINT ck_work_provider_reference
        CHECK ((provider IS NULL) = (provider_ref IS NULL));

COMMENT ON COLUMN work.provider IS
    'openlibrary | anilist | ... -- NULL when the work was typed by hand';
COMMENT ON COLUMN work.provider_ref IS
    'Identifier of the work in that provider catalog -- filled together with provider';

-- **The columns start empty and stay empty for everything already stored.** A reference
-- cannot be deduced after the fact: an entry added last month recorded which fields the
-- user saw, never which search result they came from, and re-matching a title against a
-- provider would be a guess dressed up as data. Every work that exists today therefore
-- reads as "typed by hand", and only entries added from Discover **after** this migration
-- carry a reference. Whatever #197 builds has to treat an absent reference as the normal
-- case rather than the exception -- for a long while it will be the majority.

-- Same rule applied to the editions, which needs a rewrite rather than a fresh column.
-- `CatalogEntryService` stamped `provider = 'manual'` on every edition it created --
-- unconditionally, whether the entry was typed by hand or picked straight off a live
-- Open Library or AniList hit -- and never wrote `provider_ref` at all. So the value marks
-- nothing: it does not say the entry was manual (it was stamped on catalog hits too) and
-- it cannot be resolved (there is no reference next to it). Cleared, so that `provider IS
-- NOT NULL` means the same thing on both tables, and so the CHECK below holds.
--
-- Written as "clear every half-reference" rather than "clear 'manual'": it mirrors the
-- constraint exactly, and is therefore re-runnable and true whatever a database happens to
-- hold. Today every row it touches is a 'manual' stamp.
UPDATE edition
SET provider     = NULL,
    provider_ref = NULL
WHERE (provider IS NULL) <> (provider_ref IS NULL);

ALTER TABLE edition
    ADD CONSTRAINT ck_edition_provider_reference
        CHECK ((provider IS NULL) = (provider_ref IS NULL));

-- No index on either pair. Nothing looks a work up *by* its reference: the work is matched
-- on (kind, lower(title), lower(authors), volume_number) as it always was, and a consumer
-- of the reference already holds the row it read it from. An index here would cost every
-- insert and serve no query.
