CREATE TYPE habit_type AS ENUM ('scheduled', 'counter', 'quantity');
CREATE TYPE habit_direction AS ENUM ('more', 'less');
CREATE TYPE habit_status AS ENUM ('active', 'paused', 'deleted');
CREATE TYPE checkin_status AS ENUM ('done', 'skip');

ALTER TABLE habits DROP CONSTRAINT IF EXISTS habits_habit_type_check;
ALTER TABLE habits DROP CONSTRAINT IF EXISTS habits_direction_check;
ALTER TABLE habits DROP CONSTRAINT IF EXISTS habits_status_check;
ALTER TABLE checkins DROP CONSTRAINT IF EXISTS checkins_status_check;

ALTER TABLE habits ALTER COLUMN habit_type DROP DEFAULT;
ALTER TABLE habits ALTER COLUMN status DROP DEFAULT;

ALTER TABLE habits
    ALTER COLUMN habit_type TYPE habit_type USING habit_type::habit_type,
    ALTER COLUMN direction  TYPE habit_direction USING direction::habit_direction,
    ALTER COLUMN status     TYPE habit_status USING status::habit_status;

ALTER TABLE checkins
    ALTER COLUMN status TYPE checkin_status USING status::checkin_status;

ALTER TABLE habits ALTER COLUMN habit_type SET DEFAULT 'scheduled';
ALTER TABLE habits ALTER COLUMN status     SET DEFAULT 'active';
