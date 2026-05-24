ALTER TABLE habits
    ADD COLUMN status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'paused', 'deleted'));

UPDATE habits
SET status = CASE
    WHEN deleted_at IS NOT NULL THEN 'deleted'
    WHEN paused_at IS NOT NULL THEN 'paused'
    ELSE 'active'
END;

DROP INDEX idx_habits_user_active;
CREATE INDEX idx_habits_user_active ON habits (user_id) WHERE status <> 'deleted';
