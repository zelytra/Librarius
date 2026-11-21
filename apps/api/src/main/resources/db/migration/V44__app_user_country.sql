-- The country the member says they live in (#573), an ISO 3166-1 alpha-2 code, NULL when unsaid.
-- Their own optional answer: the admins only ever see a count per country drawn from it.
ALTER TABLE app_user ADD COLUMN country VARCHAR(2);
