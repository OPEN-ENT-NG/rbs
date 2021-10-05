ALTER TABLE rbs.resource ADD COLUMN quantity SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE rbs.booking ADD COLUMN quantity SMALLINT NOT NULL DEFAULT 1;

CREATE TABLE rbs.unavailability (
	id BIGSERIAL PRIMARY KEY,
	resource_id  BIGINT NOT NULL,
	start_date TIMESTAMP NOT NULL,
	end_date TIMESTAMP NOT NULL,
    days bit(7),
	quantity SMALLINT NOT NULL DEFAULT 1,
	CONSTRAINT resource_fk FOREIGN KEY (resource_id) REFERENCES rbs.resource(id)
);