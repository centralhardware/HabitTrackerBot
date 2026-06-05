-- When a pause has a fixed duration, paused_until holds the moment it should auto-resume.
-- NULL means an indefinite pause (only a manual /resume lifts it).
ALTER TABLE habits ADD COLUMN paused_until timestamptz;
