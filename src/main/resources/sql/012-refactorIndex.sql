DROP INDEX IF EXISTS rbs.booking_end_date_idx1;
DROP INDEX IF EXISTS rbs.booking_moderator_id_idx1;
DROP INDEX IF EXISTS rbs.booking_owner_idx1;
DROP INDEX IF EXISTS rbs.booking_parent_booking_id_idx1;
DROP INDEX IF EXISTS rbs.booking_resource_id_idx1;
DROP INDEX IF EXISTS rbs.booking_start_date_idx1;
DROP INDEX IF EXISTS rbs.resource_name_idx1;
DROP INDEX IF EXISTS rbs.resource_type_id_idx1;
DROP INDEX IF EXISTS rbs.resource_type_name_idx1;
DROP INDEX IF EXISTS rbs.resource_type_owner_idx1;
DROP INDEX IF EXISTS rbs.resource_type_shares_member_id_idx1;
DROP INDEX IF EXISTS rbs.resource_type_shares_resource_id_idx1;


CREATE INDEX IF NOT EXISTS booking_is_periodic_idx ON rbs.booking USING btree (is_periodic);
CREATE INDEX IF NOT EXISTS booking_occurrences_idx ON rbs.booking USING btree (occurrences);
CREATE INDEX IF NOT EXISTS booking_school_id_idx ON rbs.resource_type USING btree (school_id);
CREATE INDEX IF NOT EXISTS booking_status_idx ON rbs.booking USING btree (status);