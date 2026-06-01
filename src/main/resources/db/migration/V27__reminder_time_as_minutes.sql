ALTER TABLE habit_reminders
    ALTER COLUMN reminder_time TYPE SMALLINT
    USING EXTRACT(HOUR FROM reminder_time)::int * 60 + EXTRACT(MINUTE FROM reminder_time)::int
          + CASE WHEN next_day THEN 1440 ELSE 0 END;
ALTER TABLE habit_reminders DROP COLUMN next_day;
