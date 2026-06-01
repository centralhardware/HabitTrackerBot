-- Multi-field quantity habits created after V24 got param_type=NULL for numeric fields
-- because AddHabitCommand did not pass paramType=NUMBER. This restores the metadata
-- so toCheckinValueRow can map stored values to the quantity column on read.
UPDATE habit_params p
SET param_type = 'number'
FROM habits h
WHERE h.id = p.habit_id
  AND h.habit_type = 'quantity'
  AND p.param_type IS NULL;
