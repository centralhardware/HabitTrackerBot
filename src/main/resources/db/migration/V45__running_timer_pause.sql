-- A running timer can be paused: the live segment is folded into `accumulated_seconds`
-- and `paused_at` is set. Elapsed time is then frozen at the accumulated total until the
-- timer is resumed (started_at reset to now, paused_at cleared) or stopped.

ALTER TABLE running_timers
    ADD COLUMN accumulated_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN paused_at           TIMESTAMPTZ;
