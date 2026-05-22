CREATE TABLE habits (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    name       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    paused_at  TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_habits_user_active ON habits (user_id) WHERE deleted_at IS NULL;

CREATE TABLE habit_reminders (
    id            BIGSERIAL PRIMARY KEY,
    habit_id      BIGINT NOT NULL REFERENCES habits (id) ON DELETE CASCADE,
    reminder_time TIME   NOT NULL,
    UNIQUE (habit_id, reminder_time)
);

CREATE INDEX idx_habit_reminders_time ON habit_reminders (reminder_time);

CREATE TABLE checkins (
    id            BIGSERIAL PRIMARY KEY,
    habit_id      BIGINT      NOT NULL REFERENCES habits (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL,
    reminder_time TIME        NOT NULL,
    check_date    DATE        NOT NULL,
    status        TEXT        NOT NULL CHECK (status IN ('done', 'skip')),
    checked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (habit_id, check_date, reminder_time)
);

CREATE INDEX idx_checkins_habit_date ON checkins (habit_id, check_date);
