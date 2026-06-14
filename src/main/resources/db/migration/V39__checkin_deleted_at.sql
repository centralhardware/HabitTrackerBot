-- Bring `checkins` in line with the soft-delete pattern: it already has the `deleted` flag,
-- add the matching `deleted_at` timestamp. Backfill existing soft-deleted rows with their
-- check-in moment as a best-effort delete time (the real moment wasn't recorded before).
ALTER TABLE checkins ADD COLUMN deleted_at TIMESTAMPTZ;
UPDATE checkins SET deleted_at = checked_at WHERE deleted = true;
