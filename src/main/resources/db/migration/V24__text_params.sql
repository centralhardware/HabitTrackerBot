CREATE TYPE param_type AS ENUM ('number', 'text');

ALTER TABLE habit_params
    ADD COLUMN param_type param_type;

UPDATE habit_params p
SET param_type = 'number'
FROM habits h
WHERE h.id = p.habit_id AND h.habit_type = 'quantity';

ALTER TABLE checkin_values
    ADD COLUMN value TEXT;

UPDATE checkin_values SET value = quantity::TEXT WHERE quantity IS NOT NULL;

ALTER TABLE checkin_values DROP COLUMN quantity;
