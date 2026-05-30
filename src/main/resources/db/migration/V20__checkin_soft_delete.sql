-- Soft-delete for check-in events: lets the MCP layer retract a mistakenly logged
-- check-in (e.g. a quantity entry made just past midnight that belonged to the
-- previous day). Deleted events stay in the table but are filtered out of every read.
ALTER TABLE checkins ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT false;
