-- Scheduled habits stored their done/skip/pending status in a checkin_values row keyed to a
-- meaningless service param. Make checkin_values.param_id nullable and let scheduled rows carry a
-- NULL param, then drop those service params — mirroring how counter habits dropped theirs (V28).

-- The (checkin_id, param_id) primary key can't hold a NULL param_id, so drop it. Its name varies
-- across environments (e.g. checkin_values_pkey vs checkin_values_pkey1), so look it up.
DO $$
DECLARE pk_name text;
BEGIN
    SELECT conname INTO pk_name
    FROM pg_constraint
    WHERE conrelid = 'checkin_values'::regclass AND contype = 'p';
    IF pk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE checkin_values DROP CONSTRAINT %I', pk_name);
    END IF;
END $$;

ALTER TABLE checkin_values ALTER COLUMN param_id DROP NOT NULL;

-- Detach scheduled value rows from their service param before the param is deleted, so the
-- param's ON DELETE CASCADE (see V21) doesn't take these rows with it.
UPDATE checkin_values v
SET param_id = NULL
FROM checkins e
WHERE v.checkin_id = e.id
  AND e.reminder_id IS NOT NULL;

DELETE FROM habit_params p
USING habits h
WHERE p.habit_id = h.id
  AND h.habit_type = 'scheduled';

-- Replace the dropped primary key with partial unique indexes: real (multi-field) params keep
-- their (checkin_id, param_id) uniqueness, while param-less scheduled rows get one-per-event
-- uniqueness on checkin_id alone.
CREATE UNIQUE INDEX IF NOT EXISTS checkin_values_param_uniq
    ON checkin_values (checkin_id, param_id) WHERE param_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS checkin_values_noparam_uniq
    ON checkin_values (checkin_id) WHERE param_id IS NULL;
