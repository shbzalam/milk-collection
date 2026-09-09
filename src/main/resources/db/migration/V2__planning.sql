-- Planning: routes and their immutable versions.
--
-- Why versions exist: the collection runs of the past must stay reproducible. A run points at
-- the exact route_version it drove, so revising a route never rewrites history. This is the
-- direct answer to "the routes were drawn on a paper map by a manager who retired in 2019" -
-- the new plan supersedes the old one instead of overwriting it.

create table route
(
    id         bigserial primary key,
    route_code varchar(32)  not null,
    name       varchar(120) not null,
    status     varchar(16)  not null,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    constraint uk_route_code unique (route_code),
    constraint ck_route_status check (status in ('ACTIVE', 'INACTIVE'))
);

create table route_version
(
    id             bigserial primary key,
    route_id       bigint      not null,
    version_number integer     not null,
    status         varchar(16) not null,
    created_at     timestamptz not null,
    published_at   timestamptz,
    constraint fk_route_version_route foreign key (route_id) references route (id),
    constraint uk_route_version_number unique (route_id, version_number),
    constraint ck_route_version_status check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    -- A draft has never been published; anything past DRAFT must carry its publication time.
    constraint ck_route_version_published_at check (
        (status = 'DRAFT' and published_at is null)
            or (status <> 'DRAFT' and published_at is not null))
);

create index idx_route_version_route on route_version (route_id);

-- A route has at most one PUBLISHED version: the current plan of record. Publishing a new
-- version archives the previous one, and this partial unique index makes that invariant a
-- database guarantee rather than a promise made by the service layer.
create unique index uk_route_version_current on route_version (route_id) where status = 'PUBLISHED';

create table route_stop
(
    id                    bigserial primary key,
    route_version_id      bigint  not null,
    collection_point_id   bigint  not null,
    sequence_number       integer not null,
    planned_arrival_time  time,
    planned_departure_time time,
    constraint fk_route_stop_version foreign key (route_version_id) references route_version (id),
    constraint fk_route_stop_collection_point foreign key (collection_point_id) references collection_point (id),
    -- Stop order must be unambiguous within a version.
    constraint uk_route_stop_sequence unique (route_version_id, sequence_number),
    -- A tanker has no reason to visit the same physical place twice in one route.
    constraint uk_route_stop_collection_point unique (route_version_id, collection_point_id),
    constraint ck_route_stop_sequence check (sequence_number > 0),
    constraint ck_route_stop_times check (
        planned_arrival_time is null or planned_departure_time is null
            or planned_departure_time >= planned_arrival_time)
);

create index idx_route_stop_route_version on route_stop (route_version_id);
create index idx_route_stop_collection_point on route_stop (collection_point_id);
