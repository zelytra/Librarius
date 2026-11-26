-- #648: the categories an import created carried no colour, and the screens drew the selected
-- state with that colour alone — a selected imported category looked unselected. They take the
-- colour a category made by hand gets (CategoryService.DEFAULT_COLOR).
UPDATE rank_category SET color = '#9a8fa6' WHERE color IS NULL AND NOT is_builtin;
