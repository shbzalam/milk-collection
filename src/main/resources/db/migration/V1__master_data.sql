-- Master data: the physical network the dairy collects from.
--
-- Design notes
--  * Business codes (village.code, farmer.farmer_code, ...) are unique because operations
--    staff identify records by code, not by surrogate id.
--  * collection_point and farmer are separate tables with a 1:N relationship: a collection
--    point is a physical place, a farmer is a person, and "some collection points serve two
--    farmers" is therefore representable without duplicating the stop.
--  * Master data is deactivated via status, never deleted, so historical runs keep resolving.
--  * Latitude/longitude are double precision to match the Java domain type exactly; the
--    application never does spatial queries, only distance arithmetic in memory.

create table village
(
    id         bigserial primary key,
    code       varchar(32)  not null,
    name       varchar(120) not null,
    latitude   double precision not null,
    longitude  double precision not null,
    status     varchar(16)  not null,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    constraint uk_village_code unique (code),
    constraint ck_village_status check (status in ('ACTIVE', 'INACTIVE')),
    constraint ck_village_latitude check (latitude between -90 and 90),
    constraint ck_village_longitude check (longitude between -180 and 180)
);

create table collection_point
(
    id         bigserial primary key,
    code       varchar(32)  not null,
    name       varchar(120) not null,
    village_id bigint       not null,
    latitude   double precision not null,
    longitude  double precision not null,
    status     varchar(16)  not null,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    constraint uk_collection_point_code unique (code),
    constraint fk_collection_point_village foreign key (village_id) references village (id),
    constraint ck_collection_point_status check (status in ('ACTIVE', 'INACTIVE')),
    constraint ck_collection_point_latitude check (latitude between -90 and 90),
    constraint ck_collection_point_longitude check (longitude between -180 and 180)
);

-- Collection points are listed per village on the planning screens.
create index idx_collection_point_village on collection_point (village_id);

create table farmer
(
    id                               bigserial primary key,
    farmer_code                      varchar(32)   not null,
    name                             varchar(120)  not null,
    phone                            varchar(20)   not null,
    village_id                       bigint        not null,
    collection_point_id              bigint        not null,
    expected_morning_quantity_litres numeric(10, 2) not null,
    expected_evening_quantity_litres numeric(10, 2) not null,
    status                           varchar(16)   not null,
    created_at                       timestamptz   not null,
    updated_at                       timestamptz   not null,
    constraint uk_farmer_code unique (farmer_code),
    constraint fk_farmer_village foreign key (village_id) references village (id),
    constraint fk_farmer_collection_point foreign key (collection_point_id) references collection_point (id),
    constraint ck_farmer_status check (status in ('ACTIVE', 'INACTIVE')),
    constraint ck_farmer_morning_qty check (expected_morning_quantity_litres >= 0),
    constraint ck_farmer_evening_qty check (expected_evening_quantity_litres >= 0)
);

-- Planning and run projection both ask "which farmers deliver at this collection point?".
create index idx_farmer_collection_point on farmer (collection_point_id);
create index idx_farmer_village on farmer (village_id);
-- "A farmer calls to ask where the tanker is" - support caller lookup by phone number.
create index idx_farmer_phone on farmer (phone);

create table tanker
(
    id                  bigserial primary key,
    tanker_code         varchar(32)   not null,
    registration_number varchar(32)   not null,
    capacity_litres     numeric(10, 2) not null,
    status              varchar(20)   not null,
    constraint uk_tanker_code unique (tanker_code),
    constraint uk_tanker_registration_number unique (registration_number),
    constraint ck_tanker_capacity check (capacity_litres > 0),
    constraint ck_tanker_status check (status in ('ACTIVE', 'IN_MAINTENANCE', 'INACTIVE'))
);

create table chilling_plant
(
    id        bigserial primary key,
    code      varchar(32)  not null,
    name      varchar(120) not null,
    latitude  double precision not null,
    longitude double precision not null,
    status    varchar(16)  not null,
    constraint uk_chilling_plant_code unique (code),
    constraint ck_chilling_plant_status check (status in ('ACTIVE', 'INACTIVE')),
    constraint ck_chilling_plant_latitude check (latitude between -90 and 90),
    constraint ck_chilling_plant_longitude check (longitude between -180 and 180)
);
