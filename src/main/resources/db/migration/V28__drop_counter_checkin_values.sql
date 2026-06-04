-- Counter habits no longer store a per-param `checkin_values` row: the `checkins` event row
-- itself is the unit being counted (see CheckInRepository.insertEvent / loadForHabit's LEFT JOIN).
-- The rows we wrote before only ever held status='done' with a NULL value, so they carry no data.
-- Drop them so old and new counter events look identical to the analytics layer.
DELETE FROM checkin_values v
USING checkins e, habits h
WHERE v.checkin_id = e.id
  AND e.habit_id = h.id
  AND h.habit_type = 'counter';
