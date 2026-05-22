DROP TABLE checkins;

CREATE TABLE checkins (
    id          BIGSERIAL PRIMARY KEY,
    reminder_id BIGINT      NOT NULL REFERENCES habit_reminders (id) ON DELETE CASCADE,
    check_date  DATE        NOT NULL,
    status      TEXT        NOT NULL CHECK (status IN ('done', 'skip')),
    checked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (reminder_id, check_date)
);

CREATE INDEX idx_checkins_date ON checkins (check_date);
