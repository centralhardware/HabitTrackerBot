-- Restrict a habit's reminders to specific weekdays.
-- NULL = every day. Values are ISO day numbers: 1=Mon .. 7=Sun.
ALTER TABLE habits ADD COLUMN reminder_days INTEGER[];
