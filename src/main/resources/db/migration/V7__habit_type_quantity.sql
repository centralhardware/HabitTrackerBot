ALTER TABLE habits
    DROP CONSTRAINT IF EXISTS habits_habit_type_check;

ALTER TABLE habits
    ADD CONSTRAINT habits_habit_type_check
    CHECK (habit_type IN ('scheduled', 'counter', 'quantity'));

ALTER TABLE habits
    ADD COLUMN target_value NUMERIC
        CHECK (target_value IS NULL OR target_value > 0),
    ADD COLUMN unit TEXT;

ALTER TABLE checkins
    ADD COLUMN quantity NUMERIC
        CHECK (quantity IS NULL OR quantity > 0);
