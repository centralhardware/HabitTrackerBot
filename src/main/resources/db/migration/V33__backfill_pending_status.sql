-- Replace the NULL-means-pending convention with the explicit 'pending' status. The only NULL
-- statuses are unanswered scheduled slots — quantity values are always 'done' and counter events
-- have no value row — so once they're filled in, status can become mandatory.
UPDATE checkin_values SET status = 'pending' WHERE status IS NULL;

ALTER TABLE checkin_values ALTER COLUMN status SET NOT NULL;
