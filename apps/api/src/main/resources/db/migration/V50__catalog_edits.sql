-- Member edits of the shared catalog, reviewed by an admin (#618), sale links on an edition
-- (#619) and the automatic check of a proposal against the store page it links to (#620).
--
-- `edition_sale_link` is catalog data like `edition` itself: where the printing can be bought,
-- one link per store, the store being derived from the URL host against a closed list
-- (`contribution.SaleStore`). It is only ever written by applying a reviewed `catalog_edit`.
--
-- `catalog_edit` is one proposal: the record it targets (an EDITION -- whose work fields travel
-- with it -- or a SERIES, no FK for the same reason as `report.target_id`), and `changes`, the
-- diff it carries: only the fields that differ, each with the value it replaces, so an applied
-- edit can be reverted. The submitter is `ON DELETE SET NULL`: erasing an account must not
-- erase the history of what was changed in a catalog every reader shares.
--
-- A notification now also tells a member what became of their proposal (type EDIT).

CREATE TABLE edition_sale_link (
    id         UUID          PRIMARY KEY,
    edition_id UUID          NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    store      VARCHAR(32)   NOT NULL,   -- contribution.SaleStore
    url        VARCHAR(1024) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_edition_sale_link_store UNIQUE (edition_id, store)
);

CREATE TABLE catalog_edit (
    id                  UUID         PRIMARY KEY,
    submitter_id        VARCHAR(255) REFERENCES app_user (id) ON DELETE SET NULL,
    target_type         VARCHAR(16)  NOT NULL,   -- EDITION | SERIES (domain.CatalogEditTarget)
    target_id           UUID         NOT NULL,
    changes             JSONB        NOT NULL,   -- {"field": {"before": "...", "after": "..."}}
    status              VARCHAR(16)  NOT NULL,   -- PENDING | APPLIED | REJECTED | REVERTED
    verification        VARCHAR(16)  NOT NULL DEFAULT 'NOT_RUN',   -- NOT_RUN | PASSED | FAILED
    verification_detail VARCHAR(32),
    verification_url    VARCHAR(1024),
    reviewer_id         VARCHAR(255) REFERENCES app_user (id) ON DELETE SET NULL,
    review_note         TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_at         TIMESTAMPTZ
);

-- The admin list reads by status, newest first.
CREATE INDEX idx_catalog_edit_status_created ON catalog_edit (status, created_at DESC);
-- One pending proposal per member per record.
CREATE UNIQUE INDEX uq_catalog_edit_pending ON catalog_edit (submitter_id, target_type, target_id)
    WHERE status = 'PENDING';

ALTER TABLE notification ADD COLUMN catalog_edit_id UUID REFERENCES catalog_edit (id) ON DELETE CASCADE;

ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type
    CHECK (type IN ('ACTIVITY', 'REACTION', 'COMMENT', 'FOLLOW', 'RELEASE', 'EDIT'));

-- An EDIT names its proposal instead of a feed event; its actor is the admin who decided, so
-- ck_notification_actor (V48) already holds for it.
ALTER TABLE notification DROP CONSTRAINT ck_notification_event;
ALTER TABLE notification ADD CONSTRAINT ck_notification_event
    CHECK (type IN ('FOLLOW', 'RELEASE') OR (type = 'EDIT' AND catalog_edit_id IS NOT NULL) OR feed_event_id IS NOT NULL);
