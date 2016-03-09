create index on rbs.booking (resource_id);
create index on rbs.booking (owner);
create index on rbs.booking (moderator_id);
create index on rbs.booking (parent_booking_id);
create index on rbs.resource (type_id);
create index on rbs.resource_type (owner);
create index on rbs.resource_type_shares (resource_id);
create index on rbs.resource_type_shares (member_id);