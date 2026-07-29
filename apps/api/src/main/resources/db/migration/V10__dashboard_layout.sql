-- Reorderable, hideable Home screen (#54).
--
-- The sections were hard-coded in HomePage.tsx: a novel reader had no use for manga
-- releases, and vice versa. One row per user holds the order they picked and which
-- sections they hid. Nothing is written until the first PUT, so an account that never
-- touches the feature costs this table exactly one indexed lookup that finds nothing —
-- the layout is computed on the fly instead (DashboardLayoutService.normalize).
--
-- `sections` is a JSONB array of `{"code": "...", "hidden": false}`, not one column per
-- section: the set of sections is meant to grow, and GET fills in whatever a stored
-- layout is missing, so a layout saved before a section existed still renders it once
-- that section ships. Plain JDBC reads and writes the column (DashboardLayoutService),
-- the same choice V5 made for catalog_cache and for the same reason: mapping a JSONB
-- column through Hibernate buys nothing for a table nothing else joins against.
--
-- V8 and V9 were already claimed by branches in flight (personalised upcoming releases,
-- custom categories) when this migration was written, so it takes the next free number
-- rather than collide with either — see DATA-MODEL.md.

CREATE TABLE dashboard_layout (
    user_id  VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    sections JSONB        NOT NULL,
    PRIMARY KEY (user_id)
);
