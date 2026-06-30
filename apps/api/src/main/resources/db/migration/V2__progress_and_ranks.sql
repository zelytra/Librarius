-- Progression de lecture (livre en cours) et rang assignable à un titre.

CREATE TABLE reading_progress (
    id              UUID PRIMARY KEY,
    library_item_id UUID NOT NULL UNIQUE REFERENCES library_item (id) ON DELETE CASCADE,
    current_page    INT,
    percent         INT,
    started_at      DATE,
    finished_at     DATE
);

-- Rang (Or/Argent/Bronze ou catégorie custom) attribué à un titre possédé.
ALTER TABLE library_item
    ADD COLUMN rank_category_id UUID REFERENCES rank_category (id) ON DELETE SET NULL;
CREATE INDEX idx_library_item_rank ON library_item (rank_category_id);
