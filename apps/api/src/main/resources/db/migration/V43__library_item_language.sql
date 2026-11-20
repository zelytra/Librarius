-- The language the reader read their copy in (#566), an ISO 639 code, NULL when unsaid. It is
-- the reader's, beside the edition's own language from the catalog.
ALTER TABLE library_item ADD COLUMN language VARCHAR(8);
