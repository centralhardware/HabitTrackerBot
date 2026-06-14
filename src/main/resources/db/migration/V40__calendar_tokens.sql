-- One subscribable iCal calendar per user. The plaintext token lives only in the URL the
-- user pastes into their calendar app; we keep just its SHA-256 hash. Content flags decide
-- which kinds of events the feed renders.
CREATE TABLE calendar_tokens (
    user_id           BIGINT      PRIMARY KEY,
    token_hash        BYTEA       NOT NULL UNIQUE,
    include_checkins  BOOLEAN     NOT NULL DEFAULT true,
    include_reminders BOOLEAN     NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at      TIMESTAMPTZ
);
