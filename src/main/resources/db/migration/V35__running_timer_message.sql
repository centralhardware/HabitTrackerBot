-- Remember which message currently shows a running timer, so a background job can edit it
-- once a minute to tick the elapsed time live. All interaction is in the user's private chat,
-- so the chat id equals the user id and need not be stored.

ALTER TABLE running_timers
    ADD COLUMN message_id BIGINT;
