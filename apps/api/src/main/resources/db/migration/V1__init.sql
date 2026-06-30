-- ── Librarius — schéma initial ────────────────────────────────────────────────
-- Principe : séparer le catalogue partagé (work / edition) de la possession par
-- utilisateur (library_item, wishlist_item). Les tables séries, progression de
-- lecture, layout du dashboard, cache catalogue, etc. seront ajoutées par des
-- migrations ultérieures (V2, V3, …) au fil des PR.

-- Utilisateur applicatif : provisionné à la volée depuis le « sub » Keycloak.
-- Aucun mot de passe n'est stocké (l'auth est déléguée à Keycloak).
CREATE TABLE app_user (
    id           VARCHAR(255) PRIMARY KEY,
    email        VARCHAR(255),
    display_name VARCHAR(255),
    locale       VARCHAR(16)  NOT NULL DEFAULT 'fr',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Œuvre du catalogue (un roman, ou un tome de manga).
CREATE TABLE work (
    id             UUID PRIMARY KEY,
    kind           VARCHAR(16)  NOT NULL,           -- BOOK | MANGA
    title          VARCHAR(512) NOT NULL,
    authors        VARCHAR(512),
    series_title   VARCHAR(512),
    volume_number  INT,
    synopsis       TEXT,
    genres         VARCHAR(512),
    original_year  INT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Édition concrète d'une œuvre (1 œuvre → N éditions : ISBN, éditeur, langue…).
CREATE TABLE edition (
    id            UUID PRIMARY KEY,
    work_id       UUID NOT NULL REFERENCES work (id) ON DELETE CASCADE,
    isbn13        VARCHAR(13),
    isbn10        VARCHAR(10),
    publisher     VARCHAR(255),
    language      VARCHAR(16),
    page_count    INT,
    cover_url     VARCHAR(1024),
    format        VARCHAR(32),
    release_date  DATE,
    provider      VARCHAR(32),
    provider_ref  VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_edition_work ON edition (work_id);
CREATE INDEX idx_edition_isbn13 ON edition (isbn13);

-- Possession d'une édition par un utilisateur.
CREATE TABLE library_item (
    id          UUID PRIMARY KEY,
    user_id     VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    edition_id  UUID NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    status      VARCHAR(16) NOT NULL DEFAULT 'OWNED',  -- OWNED | READING | READ
    rating      INT,
    acquired_at DATE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_library_user_edition UNIQUE (user_id, edition_id)
);
CREATE INDEX idx_library_user_status ON library_item (user_id, status);

-- Liste de souhaits (porte priorité / prix estimé).
CREATE TABLE wishlist_item (
    id              UUID PRIMARY KEY,
    user_id         VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    edition_id      UUID NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    priority        VARCHAR(16) NOT NULL DEFAULT 'SOON',  -- PRIORITY | SOON | SOMEDAY
    estimated_price NUMERIC(8, 2),
    note            VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_wishlist_user_edition UNIQUE (user_id, edition_id)
);
CREATE INDEX idx_wishlist_user_priority ON wishlist_item (user_id, priority);

-- Objectif de lecture annuel.
CREATE TABLE reading_goal (
    id           UUID PRIMARY KEY,
    user_id      VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    year         INT NOT NULL,
    target_count INT NOT NULL,
    unit         VARCHAR(16) NOT NULL DEFAULT 'BOOKS',  -- BOOKS | VOLUMES | PAGES
    CONSTRAINT uq_goal_user_year UNIQUE (user_id, year)
);

-- Catégories de classement : Or/Argent/Bronze (built-ins, user_id NULL) + custom.
CREATE TABLE rank_category (
    id         UUID PRIMARY KEY,
    user_id    VARCHAR(255) REFERENCES app_user (id) ON DELETE CASCADE,
    code       VARCHAR(32) NOT NULL,
    label      VARCHAR(64) NOT NULL,
    color      VARCHAR(16),
    sort_order INT NOT NULL DEFAULT 0,
    is_builtin BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_rank_category_user ON rank_category (user_id);

INSERT INTO rank_category (id, user_id, code, label, color, sort_order, is_builtin) VALUES
    ('00000000-0000-0000-0000-0000000000a1', NULL, 'or',     'Or',     '#d9b94e', 1, true),
    ('00000000-0000-0000-0000-0000000000a2', NULL, 'argent', 'Argent', '#b3b7bf', 2, true),
    ('00000000-0000-0000-0000-0000000000a3', NULL, 'bronze', 'Bronze', '#c08a5a', 3, true);
