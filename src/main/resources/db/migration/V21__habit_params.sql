-- Split a habit from the definition of its fields ("params").
--
-- Before: multi-field quantity habits were a hack — each field was its own row in
-- `habits` linked by `group_id` to a root habit; `checkins` had no `habit_id` and
-- `checkin_values(checkin_id, habit_id, ...)` pointed at the (field) habit.
--
-- After: fields live in a dedicated `habit_params` table; every habit has >=1 param
-- (scheduled/counter get one service param), `checkins` carries `habit_id` directly,
-- and `checkin_values` references `param_id` instead of `habit_id`.
--
-- A temporary `legacy_habit_id` on `habit_params` records, per param, the old habit row
-- it came from, so we can rewrite `checkin_values.habit_id -> param_id` in one join
-- (same trick as the V15 split).

CREATE TABLE habit_params (
    id              BIGSERIAL PRIMARY KEY,
    habit_id        BIGINT NOT NULL REFERENCES habits (id) ON DELETE CASCADE,
    name            TEXT,
    unit            TEXT,
    direction       habit_direction,
    daily_target    NUMERIC CHECK (daily_target IS NULL OR daily_target > 0),
    position        INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    legacy_habit_id BIGINT
);

-- 1) Group fields -> one param each, owned by the group root.
INSERT INTO habit_params (habit_id, name, unit, direction, daily_target, position, legacy_habit_id)
SELECT f.group_id, f.name, f.unit, f.direction, f.daily_target,
       (ROW_NUMBER() OVER (PARTITION BY f.group_id ORDER BY f.created_at, f.id) - 1)::int,
       f.id
FROM habits f
WHERE f.group_id IS NOT NULL AND f.group_id <> f.id;

-- 2) Single (non-group) quantity habits -> one unnamed param carrying its unit/target/direction.
INSERT INTO habit_params (habit_id, name, unit, direction, daily_target, position, legacy_habit_id)
SELECT h.id, NULL, h.unit, h.direction, h.daily_target, 0, h.id
FROM habits h
WHERE h.habit_type = 'quantity'
  AND h.group_id IS NULL;

-- 3) Scheduled / counter habits -> one service param (status lives on its checkin_values rows).
INSERT INTO habit_params (habit_id, name, unit, direction, daily_target, position, legacy_habit_id)
SELECT h.id, NULL, NULL, NULL, NULL, 0, h.id
FROM habits h
WHERE h.habit_type IN ('scheduled', 'counter');

-- 4) checkins.habit_id
ALTER TABLE checkins ADD COLUMN habit_id BIGINT;

--    scheduled events: the reminder's habit
UPDATE checkins c
SET habit_id = r.habit_id
FROM habit_reminders r
WHERE c.reminder_id = r.id
  AND c.habit_id IS NULL;

--    manual events: the value's habit, mapped field -> root
UPDATE checkins c
SET habit_id = sub.owner
FROM (
    SELECT v.checkin_id,
           MIN(CASE WHEN h.group_id IS NOT NULL AND h.group_id <> h.id
                    THEN h.group_id ELSE h.id END) AS owner
    FROM checkin_values v
    JOIN habits h ON h.id = v.habit_id
    GROUP BY v.checkin_id
) sub
WHERE c.id = sub.checkin_id
  AND c.habit_id IS NULL;

-- 5) checkin_values.param_id, via the legacy mapping
ALTER TABLE checkin_values ADD COLUMN param_id BIGINT;

UPDATE checkin_values v
SET param_id = p.id
FROM habit_params p
WHERE p.legacy_habit_id = v.habit_id;

-- 6) Drop any unresolved orphans, then tighten constraints.
DELETE FROM checkin_values WHERE param_id IS NULL;
DELETE FROM checkins WHERE habit_id IS NULL;

ALTER TABLE checkins ALTER COLUMN habit_id SET NOT NULL;
ALTER TABLE checkins
    ADD CONSTRAINT checkins_habit_id_fkey FOREIGN KEY (habit_id) REFERENCES habits (id) ON DELETE CASCADE;
CREATE INDEX idx_checkins_habit_date ON checkins (habit_id, check_date);

ALTER TABLE checkin_values DROP CONSTRAINT checkin_values_pkey;
ALTER TABLE checkin_values DROP COLUMN habit_id;
ALTER TABLE checkin_values ALTER COLUMN param_id SET NOT NULL;
ALTER TABLE checkin_values
    ADD CONSTRAINT checkin_values_param_id_fkey FOREIGN KEY (param_id) REFERENCES habit_params (id) ON DELETE CASCADE;
ALTER TABLE checkin_values ADD PRIMARY KEY (checkin_id, param_id);

-- 7) Retire the group-row hack and the temporary linkage column.
DELETE FROM habits WHERE group_id IS NOT NULL AND group_id <> id;
ALTER TABLE habits DROP COLUMN group_id;
ALTER TABLE habit_params DROP COLUMN legacy_habit_id;

CREATE INDEX idx_habit_params_habit ON habit_params (habit_id);
