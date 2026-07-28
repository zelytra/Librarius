-- Persistent catalog cache.
--
-- Catalog searches are answered from a Caffeine cache that lives inside the pod, so every
-- deployment — and there is one on every merge to `main` — throws it away and the next
-- searches go back out to Open Library and AniList. Those quotas belong to the instance as
-- a whole, so the cache almost never earns its keep.
--
-- This table is the second level: Caffeine stays in front for the microsecond hits, and
-- PostgreSQL keeps the payload across restarts.
--
-- The row is keyed by the provider that produced it and by a hash of the canonical request
-- (operation, kind, query, limit). The hash rather than the query itself: a query is free
-- text of unbounded length, and a fixed-width key keeps the primary-key index small.

CREATE TABLE catalog_cache (
    provider   VARCHAR(32)  NOT NULL,          -- openlibrary | anilist
    query_hash CHAR(64)     NOT NULL,          -- SHA-256, hex
    payload    JSONB        NOT NULL,          -- serialised List<CatalogResult>
    fetched_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- The time-to-live depends on the request type — six hours for a search, twelve for the
    -- upcoming releases — and the request type is inside the hash, so it cannot be derived
    -- from the row. Materialising the deadline keeps both the read filter and the purge to
    -- a single indexed comparison, and lets a TTL change apply only to entries written
    -- afterwards instead of retroactively expiring what is already stored.
    expires_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (provider, query_hash)
);

-- Backs the periodic purge (`DELETE ... WHERE expires_at <= now()`), which would otherwise
-- have to scan the whole table on every run.
CREATE INDEX idx_catalog_cache_expires ON catalog_cache (expires_at);
