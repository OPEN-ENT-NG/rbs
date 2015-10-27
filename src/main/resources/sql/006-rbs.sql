create index on booking (resource_id);
create index on booking (owner);
create index on booking (moderator_id);
create index on booking (parent_booking_id);
create index on resource (type_id);
create index on resource_type (owner);
create index on resource_type_shares (resource_id);
create index on resource_type_shares (member_id);