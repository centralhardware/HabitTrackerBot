-- Reorder columns into a logical order.
--
-- Postgres can't move a column in place, so each affected table is rebuilt:
-- create a fresh table with the desired column order, copy the rows, drop the old
-- table, and reattach its sequence. Only the four tables whose columns had drifted
-- out of order (from incremental ADD COLUMN / DROP COLUMN history) are rebuilt;
-- habit_reminders, user_settings, mcp_tokens and reminder_messages are already
-- in logical order and are left untouched.
--
-- Flyway runs this in a single transaction, so a failure rolls the whole thing back.

-- Detach the owned sequences so dropping the old tables doesn't drop them.
ALTER SEQUENCE habits_id_seq         OWNED BY NONE;
ALTER SEQUENCE checkin_events_id_seq OWNED BY NONE;
ALTER SEQUENCE habit_params_id_seq   OWNED BY NONE;

-- Set the renamed tables aside (constraints/FKs follow the rename).
ALTER TABLE habits         RENAME TO habits_old;
ALTER TABLE habit_params   RENAME TO habit_params_old;
ALTER TABLE checkins       RENAME TO checkins_old;
ALTER TABLE checkin_values RENAME TO checkin_values_old;

-- habit_reminders is not rebuilt; drop its FK so habits_old can be dropped later.
ALTER TABLE habit_reminders DROP CONSTRAINT habit_reminders_habit_id_fkey;

------------------------------------------------------------------------
-- New tables, columns in logical order
------------------------------------------------------------------------

CREATE TABLE habits (
    id           BIGINT       PRIMARY KEY DEFAULT nextval('habits_id_seq'),
    user_id      BIGINT       NOT NULL,
    name         TEXT         NOT NULL,
    habit_type   habit_type   NOT NULL DEFAULT 'scheduled',
    status       habit_status NOT NULL DEFAULT 'active',
    daily_target NUMERIC      CHECK (daily_target IS NULL OR daily_target > 0),
    direction    habit_direction,
    unit         TEXT,
    log_only     BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    paused_at    TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ
);

CREATE TABLE habit_params (
    id           BIGINT      PRIMARY KEY DEFAULT nextval('habit_params_id_seq'),
    habit_id     BIGINT      NOT NULL REFERENCES habits (id) ON DELETE CASCADE,
    position     INT         NOT NULL DEFAULT 0,
    name         TEXT,
    unit         TEXT,
    direction    habit_direction,
    daily_target NUMERIC     CHECK (daily_target IS NULL OR daily_target > 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE checkins (
    id          BIGINT      PRIMARY KEY DEFAULT nextval('checkin_events_id_seq'),
    user_id     BIGINT      NOT NULL,
    habit_id    BIGINT      NOT NULL REFERENCES habits (id) ON DELETE CASCADE,
    reminder_id BIGINT      REFERENCES habit_reminders (id) ON DELETE CASCADE,
    check_date  DATE        NOT NULL,
    comment     TEXT,
    checked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted     BOOLEAN     NOT NULL DEFAULT false
);

CREATE TABLE checkin_values (
    checkin_id BIGINT NOT NULL REFERENCES checkins (id) ON DELETE CASCADE,
    param_id   BIGINT NOT NULL REFERENCES habit_params (id) ON DELETE CASCADE,
    status     checkin_status,
    quantity   NUMERIC CHECK (quantity IS NULL OR quantity > 0),
    PRIMARY KEY (checkin_id, param_id)
);

------------------------------------------------------------------------
-- Copy data (parents before children)
------------------------------------------------------------------------

INSERT INTO habits (id, user_id, name, habit_type, status, daily_target, direction, unit, log_only, created_at, paused_at, deleted_at)
SELECT id, user_id, name, habit_type, status, daily_target, direction, unit, log_only, created_at, paused_at, deleted_at
FROM habits_old;

INSERT INTO habit_params (id, habit_id, position, name, unit, direction, daily_target, created_at)
SELECT id, habit_id, position, name, unit, direction, daily_target, created_at
FROM habit_params_old;

INSERT INTO checkins (id, user_id, habit_id, reminder_id, check_date, comment, checked_at, deleted)
SELECT id, user_id, habit_id, reminder_id, check_date, comment, checked_at, deleted
FROM checkins_old;

INSERT INTO checkin_values (checkin_id, param_id, status, quantity)
SELECT checkin_id, param_id, status, quantity
FROM checkin_values_old;

------------------------------------------------------------------------
-- Drop old tables (children before parents)
------------------------------------------------------------------------

DROP TABLE checkin_values_old;
DROP TABLE checkins_old;
DROP TABLE habit_params_old;
DROP TABLE habits_old;

-- Restore the habit_reminders FK against the new habits table.
ALTER TABLE habit_reminders
    ADD CONSTRAINT habit_reminders_habit_id_fkey
    FOREIGN KEY (habit_id) REFERENCES habits (id) ON DELETE CASCADE;

-- Reattach sequence ownership to the new columns.
ALTER SEQUENCE habits_id_seq         OWNED BY habits.id;
ALTER SEQUENCE checkin_events_id_seq OWNED BY checkins.id;
ALTER SEQUENCE habit_params_id_seq   OWNED BY habit_params.id;

------------------------------------------------------------------------
-- Indexes
------------------------------------------------------------------------

CREATE INDEX idx_habits_user_active ON habits (user_id) WHERE status <> 'deleted'::habit_status;

CREATE INDEX idx_habit_params_habit ON habit_params (habit_id);

CREATE UNIQUE INDEX checkins_scheduled_uniq ON checkins (reminder_id, check_date) WHERE reminder_id IS NOT NULL;
CREATE INDEX idx_checkins_user_date  ON checkins (user_id, check_date);
CREATE INDEX idx_checkins_habit_date ON checkins (habit_id, check_date);
