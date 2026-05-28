-- Split the old `checkins` table into an event table (`checkin_events`,
-- later renamed to `checkins`) and a per-habit `checkin_values` table.
-- The standalone `comments` table is folded back into the event row.

CREATE TABLE checkin_events (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    check_date  DATE        NOT NULL,
    reminder_id BIGINT      REFERENCES habit_reminders (id) ON DELETE CASCADE,
    comment     TEXT,
    checked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    legacy_comment_id BIGINT,
    legacy_checkin_id BIGINT
);

-- 1) Grouped quantity events: rows sharing comment_id collapse into one event.
INSERT INTO checkin_events (user_id, check_date, reminder_id, comment, checked_at, legacy_comment_id)
SELECT
    MIN(h.user_id),
    c.check_date,
    NULL,
    MIN(cm.body),
    MIN(c.checked_at),
    c.comment_id
FROM checkins c
JOIN habits   h  ON h.id  = c.habit_id
JOIN comments cm ON cm.id = c.comment_id
WHERE c.comment_id IS NOT NULL
GROUP BY c.comment_id, c.check_date;

-- 2) Standalone rows: one event per legacy checkin row.
INSERT INTO checkin_events (user_id, check_date, reminder_id, comment, checked_at, legacy_checkin_id)
SELECT h.user_id, c.check_date, c.reminder_id, NULL, c.checked_at, c.id
FROM checkins c
JOIN habits h ON h.id = c.habit_id
WHERE c.comment_id IS NULL;

CREATE TABLE checkin_values (
    checkin_id BIGINT         NOT NULL REFERENCES checkin_events (id) ON DELETE CASCADE,
    habit_id   BIGINT         NOT NULL REFERENCES habits (id) ON DELETE CASCADE,
    status     checkin_status,
    quantity   NUMERIC CHECK (quantity IS NULL OR quantity > 0),
    PRIMARY KEY (checkin_id, habit_id)
);

-- 3) Move per-habit data into checkin_values.
--    Grouped event: join via legacy_comment_id.
INSERT INTO checkin_values (checkin_id, habit_id, status, quantity)
SELECT e.id, c.habit_id, c.status, c.quantity
FROM checkins c
JOIN checkin_events e ON e.legacy_comment_id = c.comment_id
WHERE c.comment_id IS NOT NULL
ON CONFLICT (checkin_id, habit_id) DO NOTHING;

--    Standalone event: join via legacy_checkin_id.
INSERT INTO checkin_values (checkin_id, habit_id, status, quantity)
SELECT e.id, c.habit_id, c.status, c.quantity
FROM checkins c
JOIN checkin_events e ON e.legacy_checkin_id = c.id
WHERE c.comment_id IS NULL;

-- 4) Drop the temporary linkage columns.
ALTER TABLE checkin_events
    DROP COLUMN legacy_comment_id,
    DROP COLUMN legacy_checkin_id;

-- 5) Indexes and constraints.
CREATE UNIQUE INDEX checkins_scheduled_uniq
    ON checkin_events (reminder_id, check_date)
    WHERE reminder_id IS NOT NULL;

CREATE INDEX idx_checkins_user_date  ON checkin_events (user_id, check_date);
CREATE INDEX idx_checkin_values_habit ON checkin_values (habit_id);

-- 6) Drop the old tables and rename.
DROP TABLE checkins;
ALTER TABLE checkin_events RENAME TO checkins;
DROP TABLE comments;
