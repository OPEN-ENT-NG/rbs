CREATE SCHEMA rbs;

CREATE TABLE rbs.users (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	username VARCHAR(255)
);

CREATE TABLE rbs.groups (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	name VARCHAR(255)
);

CREATE TABLE rbs.members (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	user_id VARCHAR(36),
	group_id VARCHAR(36),
	CONSTRAINT user_fk FOREIGN KEY(user_id) REFERENCES rbs.users(id) ON UPDATE CASCADE ON DELETE CASCADE,
	CONSTRAINT group_fk FOREIGN KEY(group_id) REFERENCES rbs.groups(id) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE rbs.resource_shares (
	member_id VARCHAR(36) NOT NULL,
	resource_id BIGINT NOT NULL,
	action VARCHAR(255) NOT NULL,
	CONSTRAINT resource_share PRIMARY KEY (member_id, resource_id, action),
	CONSTRAINT resource_share_member_fk FOREIGN KEY(member_id) REFERENCES rbs.members(id) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE rbs.resource_type_shares (
	member_id VARCHAR(36) NOT NULL,
	resource_type_id BIGINT NOT NULL,
	action VARCHAR(255) NOT NULL,
	CONSTRAINT type_share PRIMARY KEY (member_id, resource_type_id, action),
	CONSTRAINT type_share_member_fk FOREIGN KEY(member_id) REFERENCES rbs.members(id) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE rbs.scripts (
	filename VARCHAR(255) NOT NULL PRIMARY KEY,
	passed TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE FUNCTION rbs.merge_users(key VARCHAR, data VARCHAR) RETURNS VOID AS
$$
BEGIN
    LOOP
        UPDATE rbs.users SET username = data WHERE id = key;
        IF found THEN
            RETURN;
        END IF;
        BEGIN
            INSERT INTO rbs.users(id,username) VALUES (key, data);
            RETURN;
        EXCEPTION WHEN unique_violation THEN
        END;
    END LOOP;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION rbs.insert_users_members() RETURNS TRIGGER AS $$
    BEGIN
		IF (TG_OP = 'INSERT') THEN
            INSERT INTO rbs.members (id, user_id) VALUES (NEW.id, NEW.id);
            RETURN NEW;
        END IF;
        RETURN NULL;
    END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION rbs.insert_groups_members() RETURNS TRIGGER AS $$
    BEGIN
		IF (TG_OP = 'INSERT') THEN
            INSERT INTO rbs.members (id, group_id) VALUES (NEW.id, NEW.id);
            RETURN NEW;
        END IF;
        RETURN NULL;
    END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_trigger
AFTER INSERT ON rbs.users
    FOR EACH ROW EXECUTE PROCEDURE rbs.insert_users_members();

CREATE TRIGGER groups_trigger
AFTER INSERT ON rbs.groups
    FOR EACH ROW EXECUTE PROCEDURE rbs.insert_groups_members();