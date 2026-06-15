-- Merge the `scheduled` and `counter` habit types into one `check` type. A check habit's
-- behavior is the product of two orthogonal facts:
--   * has a schedule (reminders) -> fired occurrences become markable done/skip slots;
--   * allows ad-hoc check-ins (allow_adhoc) -> user can log a "+1" event any time
--     (with an optional daily target + direction).
-- Both can be on at once. This file only adds the new enum value + the flag column; the data
-- migration lives in V42 because `ALTER TYPE ... ADD VALUE` can't be consumed in the same
-- transaction that adds it (mirrors the V34 timer pattern).

ALTER TYPE habit_type ADD VALUE IF NOT EXISTS 'check';

ALTER TABLE habits ADD COLUMN allow_adhoc BOOLEAN NOT NULL DEFAULT false;
