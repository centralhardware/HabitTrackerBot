-- Every remaining habit_params row belongs to a quantity habit and carries a real type
-- (number/text): scheduled and counter service params — the only ones that were ever
-- param_type NULL — were dropped in V30 and V28. Make the column mandatory.
ALTER TABLE habit_params ALTER COLUMN param_type SET NOT NULL;
