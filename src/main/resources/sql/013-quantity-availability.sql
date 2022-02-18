ALTER TABLE rbs.resource ADD COLUMN quantity BIGINT NOT NULL DEFAULT 1;
ALTER TABLE rbs.booking ADD COLUMN quantity BIGINT NOT NULL DEFAULT 1;

CREATE TABLE rbs.availability (
	id BIGSERIAL PRIMARY KEY,
	resource_id  BIGINT NOT NULL,
	start_date TIMESTAMP NOT NULL DEFAULT NOW(),
	end_date TIMESTAMP NOT NULL DEFAULT NOW(),
    start_time TIME NOT NULL DEFAULT NOW(),
    end_time TIME NOT NULL DEFAULT NOW(),
    days bit(7),
	quantity BIGINT NOT NULL DEFAULT 1,
	is_unavailability BOOLEAN NOT NULL DEFAULT FALSE,
	CONSTRAINT resource_fk FOREIGN KEY (resource_id) REFERENCES rbs.resource(id) ON UPDATE NO ACTION ON DELETE CASCADE
);