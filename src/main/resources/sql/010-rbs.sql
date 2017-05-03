ALTER TABLE rbs.resource
  ADD COLUMN color VARCHAR(7);
ALTER TABLE rbs.resource_type
  ADD COLUMN color VARCHAR(7),
  ADD COLUMN extendcolor BOOLEAN DEFAULT FALSE;


