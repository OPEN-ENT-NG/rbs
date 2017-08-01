ALTER TABLE rbs.resource
  ADD COLUMN validation BOOLEAN DEFAULT FALSE,
  ADD COLUMN color VARCHAR(7);
ALTER TABLE rbs.resource_type
  ADD COLUMN slotprofile VARCHAR (255),
  ADD COLUMN color VARCHAR(7),
  ADD COLUMN extendcolor BOOLEAN DEFAULT FALSE;

CREATE TABLE rbs.notifications (
	user_id VARCHAR (36),
	resource_id BIGINT,
	CONSTRAINT pk_notification PRIMARY KEY (user_id,resource_id),
	CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES rbs.users(id) ON UPDATE CASCADE ON DELETE CASCADE,
	CONSTRAINT fk_notification_res FOREIGN KEY (resource_id) REFERENCES rbs.resource(id) ON UPDATE CASCADE ON DELETE CASCADE
);


