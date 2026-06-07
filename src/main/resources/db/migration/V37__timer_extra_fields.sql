-- Timer habits can now carry extra annotation fields ("comment" params) the user fills
-- in before starting and/or after stopping the timer — same multi-field mechanism as
-- quantity habits, but driven through Telegram and split across the start/stop moments.
--
-- Each extra param records its phase ('before' | 'after'); the timer's own elapsed-seconds
-- param keeps timer_phase NULL. Extra params never participate in any statistics — they
-- are pure annotations, excluded from the analytics layer by their non-null phase.
ALTER TABLE habit_params ADD COLUMN timer_phase TEXT
    CHECK (timer_phase IS NULL OR timer_phase IN ('before', 'after'));

-- Holds the values the user typed for the "before" fields while the timer runs, carried
-- until stop writes them onto the resulting check-in. A JSON object {param_id: text}.
ALTER TABLE running_timers ADD COLUMN pending_values JSONB;
