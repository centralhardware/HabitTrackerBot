-- Soft-delete for habit fields/params: a deleted field is hidden from active habit display and
-- data entry, but its row (and its historical checkin_values) stay put so past records still
-- resolve the field's name and unit. Uses the `deleted` flag + `deleted_at` timestamp pair.
ALTER TABLE habit_params ADD COLUMN deleted    BOOLEAN     NOT NULL DEFAULT false;
ALTER TABLE habit_params ADD COLUMN deleted_at TIMESTAMPTZ;
