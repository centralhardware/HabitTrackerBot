-- Timer habits auto-track time spent on an activity: the user starts a timer and
-- stops it later; the elapsed minutes are recorded as a numeric check-in (reusing the
-- single NUMBER param that single-field quantity habits use).
--
-- The in-flight start moment lives in `running_timers` (one running timer per habit);
-- stopping deletes the row and writes the elapsed minutes as a checkin value.

ALTER TYPE habit_type ADD VALUE IF NOT EXISTS 'timer';

CREATE TABLE running_timers (
    habit_id   BIGINT PRIMARY KEY REFERENCES habits (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_running_timers_user ON running_timers (user_id);
