-- Milk collection: the actual transactions with farmers.
--
-- A collection belongs to a run_stop AND a farmer. That pair is what makes "some collection
-- points serve two farmers" work: CP-001 is one physical stop on the route, and the two
-- farmers who deliver there produce two milk_collection rows against that single stop.

create table milk_collection
(
    id              bigserial primary key,
    run_stop_id     bigint        not null,
    farmer_id       bigint        not null,
    quantity_litres numeric(10, 2) not null,
    collected_at    timestamptz   not null,
    status          varchar(24)   not null,
    created_at      timestamptz   not null,
    constraint fk_milk_collection_run_stop foreign key (run_stop_id) references run_stop (id),
    constraint fk_milk_collection_farmer foreign key (farmer_id) references farmer (id),
    -- One collection per farmer per stop. The service checks this first for a clear error
    -- message; the constraint is what makes a double-tap on a field device impossible.
    constraint uk_milk_collection_stop_farmer unique (run_stop_id, farmer_id),
    constraint ck_milk_collection_quantity check (quantity_litres > 0),
    constraint ck_milk_collection_status check (status in ('ACCEPTED'))
);

-- Capacity accounting sums a run's litres through run_stop on every collection.
create index idx_milk_collection_run_stop on milk_collection (run_stop_id);
-- A farmer's collection history ("was my milk recorded?").
create index idx_milk_collection_farmer on milk_collection (farmer_id);
