CREATE TABLE mcp_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    token_hash   BYTEA       NOT NULL UNIQUE,
    label        TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_mcp_tokens_user_active ON mcp_tokens (user_id) WHERE revoked_at IS NULL;
