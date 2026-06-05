-- Replace the NULL-means-pending convention with explicit statuses, then make status mandatory.

-- Unanswered scheduled slots become the explicit 'pending' status.
UPDATE checkin_values v
SET status = 'pending'
FROM checkins e
WHERE v.checkin_id = e.id
  AND e.reminder_id IS NOT NULL
  AND v.status IS NULL;

-- A handful of legacy manual value rows were stored without a status; a stored value means the
-- entry was logged, so they are 'done' (not pending).
UPDATE checkin_values
SET status = 'done'
WHERE status IS NULL
  AND value IS NOT NULL;

ALTER TABLE checkin_values ALTER COLUMN status SET NOT NULL;
