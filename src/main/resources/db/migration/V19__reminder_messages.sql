-- Remember every scheduled-reminder message we send, so that when its check-in is
-- resolved (done/skip) — via any single button or by auto-skip — all the duplicate
-- messages we sent for the same (reminder, date) can be rewritten and their buttons
-- dropped at once. Rows are deleted once the check-in is settled.
CREATE TABLE reminder_messages (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    message_id  BIGINT      NOT NULL,
    reminder_id BIGINT      NOT NULL REFERENCES habit_reminders (id) ON DELETE CASCADE,
    check_date  DATE        NOT NULL,
    text        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reminder_messages_lookup ON reminder_messages (reminder_id, check_date);
