-- Ad-hoc "+1" check-ins are gone: a check track is now scheduled-only. Drop the flag that used
-- to mark a track as accepting arbitrary check-ins. Old ad-hoc events (bare `checkins` rows with
-- no reminder) stay in place as history; nothing writes new ones.
ALTER TABLE tracks DROP COLUMN allow_adhoc;
