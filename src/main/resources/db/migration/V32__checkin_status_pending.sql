-- A scheduled check-in that has been sent but not yet answered used to be modelled as a NULL
-- status. Make "pending" a first-class value of the enum instead. (Adding the value must commit
-- before it can be used, so the backfill lives in the next migration / transaction.)
ALTER TYPE checkin_status ADD VALUE IF NOT EXISTS 'pending';
