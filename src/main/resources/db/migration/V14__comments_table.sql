CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    body       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE checkins
    ADD COLUMN comment_id BIGINT;

-- Pre-allocate comment ids per existing row with comment, then insert into comments
UPDATE checkins
SET comment_id = nextval(pg_get_serial_sequence('comments', 'id'))
WHERE comment IS NOT NULL;

INSERT INTO comments (id, body, created_at)
SELECT comment_id, comment, checked_at
FROM checkins
WHERE comment_id IS NOT NULL;

ALTER TABLE checkins
    ADD CONSTRAINT checkins_comment_id_fkey
    FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE SET NULL;

CREATE INDEX idx_checkins_comment ON checkins (comment_id) WHERE comment_id IS NOT NULL;

ALTER TABLE checkins DROP COLUMN comment;
