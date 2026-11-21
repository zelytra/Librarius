-- Platine, a fourth metal above Or (#608). A shared built-in like the others (user_id NULL),
-- read-only through CategoryService, sorted before Or: sort_order 0, Or keeping its 1. The
-- label is French like its siblings, displayed as-is. No account can hold a custom category
-- coded `platine` already: none existed on staging when this shipped, production is not open,
-- and from here on CategoryService refuses the name, a built-in code being taken.
INSERT INTO rank_category (id, user_id, code, label, color, sort_order, is_builtin) VALUES
    ('00000000-0000-0000-0000-0000000000a5', NULL, 'platine', 'Platine', '#8fa9bd', 0, true)
ON CONFLICT (id) DO NOTHING;
