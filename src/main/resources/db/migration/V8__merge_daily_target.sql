ALTER TABLE habits
    ALTER COLUMN daily_target TYPE NUMERIC USING daily_target::numeric;

UPDATE habits
SET daily_target = target_value
WHERE habit_type = 'quantity' AND target_value IS NOT NULL;

ALTER TABLE habits DROP COLUMN target_value;
