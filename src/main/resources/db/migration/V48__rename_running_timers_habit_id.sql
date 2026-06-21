-- Follow-up to V47: the running_timers table was missed in the habit→track rename.
ALTER TABLE running_timers RENAME COLUMN habit_id TO track_id;
