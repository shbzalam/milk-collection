-- Operations: the twice-a-day execution of a plan.
--
-- A collection_run points at a route_version, never at a route. That single choice is what
-- keeps history reproducible: re-reading a run from three months ago yields the exact stop
-- list that was driven, even though the route has been revised twice since.

create sequence run_number_seq start with 1 increment by 1;

create table collection_run
(
    id                 bigserial primary key,
    run_number         varchar(32) not null,
    route_version_id   bigint      not null,
    tanker_id          bigint      not null,
    chilling_plant_id  bigint      not null,
    run_date           date        not null,
    shift              varchar(10) not null,
    planned_start_time timestamptz not null,
    actual_start_time  timestamptz,
    actual_end_time    timestamptz,
    status             varchar(16) not null,
    -- Optimistic locking for state transitions (see RunExecutionService). Defaulted so that a
    -- plain SQL insert cannot violate the NOT NULL constraint; JPA always supplies its own value.
    version            bigint      not null default 0,
    constraint uk_collection_run_number unique (run_number),
    constraint fk_collection_run_route_version foreign key (route_version_id) references route_version (id),
    constraint fk_collection_run_tanker foreign key (tanker_id) references tanker (id),
    constraint fk_collection_run_chilling_plant foreign key (chilling_plant_id) references chilling_plant (id),
    constraint ck_collection_run_shift check (shift in ('MORNING', 'EVENING')),
    constraint ck_collection_run_status check (
        status in ('PLANNED', 'STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    constraint ck_collection_run_times check (
        actual_end_time is null or actual_start_time is not null)
);

-- One tanker cannot be booked twice for the same date and shift. Cancelling a run releases
-- the slot, hence the partial index rather than a plain unique constraint.
create unique index uk_collection_run_tanker_slot
    on collection_run (tanker_id, run_date, shift) where status <> 'CANCELLED';

create index idx_collection_run_tanker on collection_run (tanker_id);
create index idx_collection_run_route_version on collection_run (route_version_id);
-- The daily dispatch view is "today, this shift"; ETA lookups filter on run_date too.
create index idx_collection_run_date_shift on collection_run (run_date, shift);

create table run_stop
(
    id                    bigserial primary key,
    collection_run_id     bigint      not null,
    route_stop_id         bigint      not null,
    sequence_number       integer     not null,
    -- Projected at run creation from the planned start, the travel-time model and the
    -- service-time model - the same projection the feasibility check approved.
    planned_arrival_time  timestamptz not null,
    actual_arrival_time   timestamptz,
    actual_departure_time timestamptz,
    status                varchar(16) not null,
    constraint fk_run_stop_run foreign key (collection_run_id) references collection_run (id),
    constraint fk_run_stop_route_stop foreign key (route_stop_id) references route_stop (id),
    constraint uk_run_stop_sequence unique (collection_run_id, sequence_number),
    constraint uk_run_stop_route_stop unique (collection_run_id, route_stop_id),
    constraint ck_run_stop_status check (
        status in ('PENDING', 'ARRIVED', 'COLLECTING', 'COMPLETED', 'SKIPPED')),
    constraint ck_run_stop_times check (
        actual_departure_time is null or actual_arrival_time is not null)
);

create index idx_run_stop_collection_run on run_stop (collection_run_id);
create index idx_run_stop_route_stop on run_stop (route_stop_id);
