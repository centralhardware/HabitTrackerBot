ALTER TABLE habits
    ADD COLUMN group_id BIGINT REFERENCES habits (id) ON DELETE SET NULL;

CREATE INDEX idx_habits_group ON habits (group_id) WHERE group_id IS NOT NULL;
