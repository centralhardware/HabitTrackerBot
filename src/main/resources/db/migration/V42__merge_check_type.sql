-- Data migration for the scheduled+counter -> check merge (see V41).
--
-- Existing counters allowed arbitrary check-ins, so they become check habits with allow_adhoc=true.
-- Existing scheduled habits only marked their reminder slots, so they become check habits with
-- allow_adhoc=false (the default). The old 'scheduled'/'counter' enum values are left in place as
-- dead values — Postgres can't drop enum values, but nothing references them after this update.

UPDATE habits SET allow_adhoc = true WHERE habit_type = 'counter';

UPDATE habits SET habit_type = 'check' WHERE habit_type IN ('scheduled', 'counter');
