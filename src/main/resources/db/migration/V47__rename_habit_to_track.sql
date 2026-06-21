-- Rebrand: "habit" entity renamed to "track" (project → TrackAll).
-- Renames tables, the habit_id/habit_type columns, owned sequences and enum types.
-- Auto-named constraints/indexes (e.g. habits_pkey) keep their old names; they stay
-- functional and are not referenced from code, so they are intentionally left as-is.

ALTER TABLE habits RENAME TO tracks;
ALTER TABLE tracks RENAME COLUMN habit_type TO track_type;
ALTER SEQUENCE habits_id_seq RENAME TO tracks_id_seq;

ALTER TABLE habit_params RENAME TO track_params;
ALTER TABLE track_params RENAME COLUMN habit_id TO track_id;
ALTER SEQUENCE habit_params_id_seq RENAME TO track_params_id_seq;

ALTER TABLE habit_reminders RENAME TO track_reminders;
ALTER TABLE track_reminders RENAME COLUMN habit_id TO track_id;
ALTER SEQUENCE habit_reminders_id_seq RENAME TO track_reminders_id_seq;

ALTER TABLE checkins RENAME COLUMN habit_id TO track_id;

ALTER TYPE habit_type RENAME TO track_type;
ALTER TYPE habit_status RENAME TO track_status;
ALTER TYPE habit_direction RENAME TO track_direction;
