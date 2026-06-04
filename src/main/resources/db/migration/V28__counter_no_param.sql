-- Counter habits no longer carry a "service" param or per-event value rows.
--
-- A counter event is now just a row in `checkins`; its daily count is the number of
-- such rows on a date (status/param/value never carried any information for counters —
-- analytics already counted rows, ignoring them). Scheduled habits keep their service
-- param, since their done/skip status still lives on `checkin_values`.
--
-- Deleting the counter service params cascades to their `checkin_values` rows
-- (checkin_values.param_id FK is ON DELETE CASCADE, see V21), leaving the bare
-- `checkins` events intact.

DELETE FROM habit_params p
USING habits h
WHERE p.habit_id = h.id
  AND h.habit_type = 'counter';
