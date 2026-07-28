# Modèle de données — Librarius

Source de vérité : `apps/api/src/main/resources/db/migration/`.
Hibernate est en `validate` — le schéma Flyway **est** le modèle.

## 1. Schéma actuel (V1 + V2)

```
app_user ──┬─< library_item >── edition >── work
           ├─< wishlist_item >──┘
           ├─< reading_goal
           └─< rank_category (custom)          library_item ──1:1─ reading_progress
                    ▲                                │
              rank_category (built-ins, user_id NULL)┘
```

### Tables

| Table | Clé | Colonnes notables | Contraintes |
|---|---|---|---|
| `app_user` | `id VARCHAR(255)` = `sub` Keycloak | `email`, `display_name`, `locale` (défaut `fr`) | Aucun credential stocké |
| `work` | `id UUID` | `kind` (BOOK\|MANGA), `title`, `authors`, `series_title`, `volume_number`, `synopsis`, `genres`, `original_year` | — |
| `edition` | `id UUID` | `work_id` FK, `isbn13`, `isbn10`, `publisher`, `language`, `page_count`, `cover_url`, `format`, `release_date`, `provider`, `provider_ref` | idx sur `work_id`, `isbn13` |
| `library_item` | `id UUID` | `user_id` FK, `edition_id` FK, `status` (OWNED\|READING\|READ), `rating`, `acquired_at`, `rank_category_id` FK | `UNIQUE(user_id, edition_id)`, idx `(user_id, status)` |
| `reading_progress` | `id UUID` | `library_item_id` **UNIQUE** FK, `current_page`, `percent`, `started_at`, `finished_at` | 1:1 avec `library_item` |
| `wishlist_item` | `id UUID` | `user_id` FK, `edition_id` FK, `priority` (PRIORITY\|SOON\|SOMEDAY), `estimated_price NUMERIC(8,2)`, `note` | `UNIQUE(user_id, edition_id)` |
| `reading_goal` | `id UUID` | `user_id` FK, `year`, `target_count`, `unit` (BOOKS\|VOLUMES\|PAGES) | `UNIQUE(user_id, year)` |
| `rank_category` | `id UUID` | `user_id` FK **nullable**, `code`, `label`, `color`, `sort_order`, `is_builtin` | `user_id NULL` = built-in partagé |

Built-ins insérés en V1 : `or` (#d9b94e), `argent` (#b3b7bf), `bronze` (#c08a5a).

### Cascades

Toutes les FK vers `app_user` sont en `ON DELETE CASCADE` : supprimer un `app_user`
efface l'intégralité de ses données — utile pour la suppression de compte RGPD.
`library_item.rank_category_id` est en `ON DELETE SET NULL`.

## 2. Limites connues du modèle actuel

| # | Limite | Impact |
|---|---|---|
| L1 | **Pas de table `series`** — la série est une chaîne libre `work.series_title` | Impossible de compter les tomes d'une série, de suivre une série, de détecter les tomes manquants. Dédoublonnage par `toLowerCase()` dans `StatsResource` |
| L2 | **`genres` est une `VARCHAR(512)`** libre, traitée comme atomique | « Fantasy, Aventure » ≠ « Fantasy » dans les stats ; pas de filtre par genre fiable |
| L3 | **`authors` est une chaîne** | Pas de fiche auteur, pas de regroupement, pas de recherche par auteur exacte |
| L4 | **Pas d'historique de lecture** | Une relecture écrase `started_at`/`finished_at` |
| L5 | **Pas de `catalog_cache`** | Le cache Caffeine disparaît au redémarrage → pression sur les quotas providers |
| L6 | **Pas de `dashboard_layout`** | Les sections de l'Accueil sont figées dans le code |
| L7 | **Pas de `notification_pref`** ni de canal de notification | Aucune alerte possible |
| L8 | **Pas d'`upcoming_release`** curée | Impossible de proposer des dates de sortie VF |
| L9 | Pas de `series_followed` | Les prochaines sorties ne peuvent pas être personnalisées |

## 3. Évolutions planifiées

### V3 — Séries (milestone « Cœur produit »)

```sql
CREATE TABLE series (
    id            UUID PRIMARY KEY,
    kind          VARCHAR(16)  NOT NULL,          -- BOOK | MANGA
    title         VARCHAR(512) NOT NULL,
    original_title VARCHAR(512),
    total_volumes INT,                            -- NULL si inconnu / en cours
    status        VARCHAR(16),                    -- ONGOING | COMPLETED | HIATUS
    cover_url     VARCHAR(1024),
    synopsis      TEXT,
    provider      VARCHAR(32),
    provider_ref  VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE work ADD COLUMN series_id UUID REFERENCES series (id) ON DELETE SET NULL;
CREATE INDEX idx_work_series ON work (series_id, volume_number);

CREATE TABLE series_follow (
    user_id   VARCHAR(255) NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    series_id UUID         NOT NULL REFERENCES series (id)   ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, series_id)
);
```

Migration des données : créer une `series` par `work.series_title` distinct (par `kind`),
rattacher les `work`, **conserver `series_title`** en lecture seule le temps d'une
version pour ne pas casser le front, puis la supprimer en V4.

### V4 — Genres normalisés & historique

```sql
CREATE TABLE genre (id UUID PRIMARY KEY, code VARCHAR(64) UNIQUE NOT NULL, label VARCHAR(64) NOT NULL);
CREATE TABLE work_genre (work_id UUID REFERENCES work(id) ON DELETE CASCADE,
                         genre_id UUID REFERENCES genre(id) ON DELETE CASCADE,
                         PRIMARY KEY (work_id, genre_id));

CREATE TABLE reading_session (
    id              UUID PRIMARY KEY,
    library_item_id UUID NOT NULL REFERENCES library_item (id) ON DELETE CASCADE,
    started_at      DATE,
    finished_at     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### V5 — Personnalisation & notifications

`dashboard_layout (user_id PK, sections JSONB)`,
`notification_pref (user_id PK, prefs JSONB)`,
`upcoming_release (id, series_id, volume_number, release_date, region, publisher, source)`.

### V6 — Cache catalogue persistant

`catalog_cache (provider, query_hash, payload JSONB, fetched_at, PRIMARY KEY (provider, query_hash))`.

## 4. Règles d'écriture des migrations

1. Nommage `V<n>__snake_case_description.sql`, numérotation strictement croissante.
2. **Ne jamais modifier une migration déjà mergée sur `develop`** — Flyway échoue sur
   checksum divergent. Corriger par une nouvelle migration.
3. Migration de données incluse dans la même migration que le changement de structure.
4. Ajouter la colonne en **nullable**, remplir, puis contraindre — en trois étapes si
   la table est volumineuse.
5. Mettre à jour l'entité Panache **et** ce document dans la même PR.
6. Vérifier localement : `pnpm infra:up && cd apps/api && ./mvnw quarkus:dev` doit
   démarrer sans erreur de validation Hibernate.
