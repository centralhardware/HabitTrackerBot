-- Move weekday filtering from the habit to the individual reminder, so a single habit
-- can fire on different days at different times (e.g. weekdays 08:00, weekends 10:00).
-- NULL = every day. Values are ISO day numbers: 1=Mon .. 7=Sun.
ALTER TABLE habit_reminders ADD COLUMN reminder_days INTEGER[];

UPDATE habit_reminders r
SET reminder_days = h.reminder_days
FROM habits h
WHERE h.id = r.habit_id AND h.reminder_days IS NOT NULL;

ALTER TABLE habits DROP COLUMN reminder_days;
