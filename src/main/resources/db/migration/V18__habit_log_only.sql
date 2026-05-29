-- "Log mode": a habit that is just a journal of events, with no targets, streaks,
-- completion rates or trends. Such habits still take check-ins and reminders, but are
-- excluded from /stats and the weekly summary. Applies to any habit type.
ALTER TABLE habits ADD COLUMN log_only BOOLEAN NOT NULL DEFAULT false;
