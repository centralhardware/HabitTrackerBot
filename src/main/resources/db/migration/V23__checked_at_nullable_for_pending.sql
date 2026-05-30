-- A pending scheduled check-in has no real check-in moment yet: the slot fired,
-- but the user hasn't acted on it. Storing the firing time (or now()) in
-- checked_at was misleading. Make the column nullable so pending rows can carry
-- NULL, and only get a timestamp once they're resolved (done / skip / auto-skip).

ALTER TABLE checkins ALTER COLUMN checked_at DROP NOT NULL;
ALTER TABLE checkins ALTER COLUMN checked_at DROP DEFAULT;

-- Clear the bogus timestamp on existing pending scheduled rows.
UPDATE checkins e
SET checked_at = NULL
WHERE e.reminder_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM checkin_values v
      WHERE v.checkin_id = e.id AND v.status IS NULL
  );
