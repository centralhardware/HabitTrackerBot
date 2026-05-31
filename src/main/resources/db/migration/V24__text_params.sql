CREATE TYPE param_type AS ENUM ('number', 'text');

ALTER TABLE habit_params
    ADD COLUMN param_type param_type NOT NULL DEFAULT 'number';

ALTER TABLE checkin_values
    ADD COLUMN value TEXT;

UPDATE checkin_values SET value = quantity::TEXT WHERE quantity IS NOT NULL;

ALTER TABLE checkin_values DROP COLUMN quantity;
