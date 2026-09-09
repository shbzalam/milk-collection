-- Tanker tracking: the position history that answers "where is the tanker?".
--
-- Index choices are driven by the two queries that exist:
--   * latest position of a tanker            -> (tanker_id, recorded_at desc)
--   * latest position on a run, used for ETA -> (collection_run_id, recorded_at desc)
-- Both are covered by composite indexes ending in a descending recorded_at, so
-- "order by recorded_at desc limit 1" is an index scan of one row. A standalone index on
-- recorded_at is deliberately NOT created: nothing queries by time alone, and a retention job
-- deleting old rows would scan regardless.

create table tanker_location
(
    id                bigserial primary key,
    tanker_id         bigint      not null,
    -- Nullable on purpose: a tanker reports its position whether or not it is on a run.
    -- Pings received while a run is on the road are attached to it automatically.
    collection_run_id bigint,
    latitude          double precision not null,
    longitude         double precision not null,
    recorded_at       timestamptz not null,
    constraint fk_tanker_location_tanker foreign key (tanker_id) references tanker (id),
    constraint fk_tanker_location_run foreign key (collection_run_id) references collection_run (id),
    constraint ck_tanker_location_latitude check (latitude between -90 and 90),
    constraint ck_tanker_location_longitude check (longitude between -180 and 180)
);

create index idx_tanker_location_tanker_recorded on tanker_location (tanker_id, recorded_at desc);
create index idx_tanker_location_run_recorded on tanker_location (collection_run_id, recorded_at desc);
