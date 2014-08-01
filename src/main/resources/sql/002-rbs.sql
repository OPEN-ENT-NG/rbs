CREATE TABLE rbs.resource_type(
	id BIGSERIAL PRIMARY KEY,
	owner VARCHAR(36) NOT NULL,
	created TIMESTAMP NOT NULL DEFAULT NOW(),
	modified TIMESTAMP NOT NULL DEFAULT NOW(),
	visibility VARCHAR(9),
	CONSTRAINT type_owner_fk FOREIGN KEY(owner) REFERENCES rbs.users(id) ON UPDATE CASCADE ON DELETE CASCADE,

	name VARCHAR(255) NOT NULL,
	validation BOOLEAN NOT NULL,
	school_id VARCHAR(255) NOT NULL
);

ALTER TABLE rbs.resource_type_shares ADD CONSTRAINT resource_type_fk FOREIGN KEY(resource_id) REFERENCES rbs.resource_type(id) ON UPDATE CASCADE ON DELETE CASCADE;


CREATE TABLE rbs.resource(
	id BIGSERIAL PRIMARY KEY,
	owner VARCHAR(36) NOT NULL,
	created TIMESTAMP NOT NULL DEFAULT NOW(),
	modified TIMESTAMP NOT NULL DEFAULT NOW(),
	visibility VARCHAR(9),
	CONSTRAINT resource_owner_fk FOREIGN KEY(owner) REFERENCES rbs.users(id) ON UPDATE CASCADE ON DELETE CASCADE,

	name VARCHAR(255) NOT NULL,
	description TEXT,
	icon VARCHAR(255),
	periodic_booking BOOLEAN NOT NULL,
	is_available BOOLEAN NOT NULL,
	min_delay BIGINT,
	max_delay BIGINT,
	type_id BIGINT NOT NULL,
	CONSTRAINT type_fk FOREIGN KEY(type_id) REFERENCES rbs.resource_type(id) ON UPDATE CASCADE ON DELETE CASCADE,
	CONSTRAINT valid_delays CHECK (min_delay >= 0 AND max_delay >= min_delay)
);

ALTER TABLE rbs.resource_shares ADD CONSTRAINT resource_fk FOREIGN KEY(resource_id) REFERENCES rbs.resource(id) ON UPDATE CASCADE ON DELETE CASCADE;


CREATE TABLE rbs.booking(
	id BIGSERIAL PRIMARY KEY,
	resource_id BIGSERIAL NOT NULL,
	owner VARCHAR(36) NOT NULL,
	booking_reason TEXT,
	created TIMESTAMP NOT NULL DEFAULT NOW(),
	modified TIMESTAMP NOT NULL DEFAULT NOW(),
	start_date TIMESTAMP NOT NULL,
	end_date TIMESTAMP NOT NULL,
	status SMALLINT,
	moderator_id VARCHAR(36),
	refusal_reason TEXT,
	parent_booking_id BIGINT,
	CONSTRAINT resource_fk FOREIGN KEY(resource_id) REFERENCES rbs.resource(id) ON UPDATE CASCADE,
	CONSTRAINT owner_fk FOREIGN KEY(owner) REFERENCES rbs.users(id) ON UPDATE CASCADE,
	CONSTRAINT moderator_fk FOREIGN KEY(moderator_id) REFERENCES rbs.users(id) ON UPDATE CASCADE,
	CONSTRAINT parent_booking_fk FOREIGN KEY(parent_booking_id) REFERENCES rbs.booking(id) ON UPDATE CASCADE ON DELETE CASCADE
);
