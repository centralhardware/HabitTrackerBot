ALTER TABLE habits
    ADD COLUMN habit_type   TEXT NOT NULL DEFAULT 'scheduled'
        CHECK (habit_type IN ('scheduled', 'counter')),
    ADD COLUMN daily_target INTEGER
        CHECK (daily_target IS NULL OR daily_target > 0),
    ADD COLUMN direction    TEXT
        CHECK (direction IS NULL OR direction IN ('more', 'less'));

ALTER TABLE checkins
    ADD COLUMN habit_id BIGINT REFERENCES habits (id) ON DELETE CASCADE;

UPDATE checkins c
SET habit_id = r.habit_id
FROM habit_reminders r
WHERE c.reminder_id = r.id;

ALTER TABLE checkins ALTER COLUMN habit_id SET NOT NULL;
ALTER TABLE checkins ALTER COLUMN reminder_id DROP NOT NULL;

ALTER TABLE checkins DROP CONSTRAINT IF EXISTS checkins_reminder_id_check_date_key;

CREATE UNIQUE INDEX checkins_scheduled_uniq
    ON checkins (reminder_id, check_date)
    WHERE reminder_id IS NOT NULL;

CREATE INDEX idx_checkins_habit_date ON checkins (habit_id, check_date);
