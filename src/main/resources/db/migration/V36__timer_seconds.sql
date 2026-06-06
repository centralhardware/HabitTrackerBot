-- Timer habits switch from storing minutes to storing seconds, so that logged
-- elapsed time is an exact whole-second count instead of a rounded fractional
-- minute. Both the logged values and the daily targets are scaled by 60.

-- Logged durations live as numeric text on the timer's single NUMBER param.
UPDATE checkin_values v
SET value = ((v.value::numeric) * 60)::text
FROM habit_params p
JOIN habits h ON h.id = p.habit_id
WHERE v.param_id = p.id
  AND h.habit_type = 'timer'
  AND v.value ~ '^-?[0-9]+(\.[0-9]+)?$';

-- Daily targets on the habit row.
UPDATE habits
SET daily_target = daily_target * 60
WHERE habit_type = 'timer' AND daily_target IS NOT NULL;

-- Daily targets carried on the param row (single-field timers may store it there).
UPDATE habit_params p
SET daily_target = p.daily_target * 60
FROM habits h
WHERE h.id = p.habit_id AND h.habit_type = 'timer' AND p.daily_target IS NOT NULL;
