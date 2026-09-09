-- Demo dataset. Loaded only when the "demo" profile is active (see application-demo.yml),
-- so tests never see it.
--
-- Deliberately small: 5 villages, 8 collection points, 16 farmers, 3 tankers, 1 chilling plant
-- and two published routes. The real dairy has ~1,400 farmers across 60 villages with 22
-- tankers; hand-writing that would add nothing a reviewer cannot already see, and the volume
-- characteristics are covered by indexes and paging rather than by seed rows.
--
-- Written as a repeatable Flyway migration with ON CONFLICT DO NOTHING throughout, so it is
-- idempotent: re-running it against a populated database is a no-op. Foreign keys are resolved
-- by business code, never by hardcoded ids.
--
-- Coordinates are in the Shirur area of Pune district, Maharashtra. Both routes are feasible
-- under the configured 4-hour holding limit and the 30 km/h travel model.

insert into village (code, name, latitude, longitude, status, created_at, updated_at)
select d.code, d.name, d.lat, d.lon, 'ACTIVE', now(), now()
from (values ('V-001', 'Shirur', 18.8280, 74.3700),
             ('V-002', 'Kendur', 18.7800, 74.3100),
             ('V-003', 'Pabal', 18.8900, 74.2600),
             ('V-004', 'Nimgaon', 18.7400, 74.4300),
             ('V-005', 'Talegaon', 18.9100, 74.4400)) as d(code, name, lat, lon)
on conflict (code) do nothing;

insert into chilling_plant (code, name, latitude, longitude, status)
select 'PLANT-SHIRUR', 'Shirur Chilling Centre', 18.8237, 74.3732, 'ACTIVE'
on conflict (code) do nothing;

insert into tanker (tanker_code, registration_number, capacity_litres, status)
select d.code, d.registration, d.capacity, d.status
from (values ('TNK-01', 'MH12AB1234', 5000.00, 'ACTIVE'),
             ('TNK-02', 'MH12AB5678', 5000.00, 'ACTIVE'),
             ('TNK-03', 'MH12AB9012', 3000.00, 'IN_MAINTENANCE'))
         as d(code, registration, capacity, status)
on conflict (tanker_code) do nothing;

insert into collection_point (code, name, village_id, latitude, longitude, status, created_at, updated_at)
select d.code, d.name, v.id, d.lat, d.lon, 'ACTIVE', now(), now()
from (values ('CP-001', 'Shirur Chowk', 'V-001', 18.8290, 74.3710),
             ('CP-002', 'Shirur Dairy Gate', 'V-001', 18.8340, 74.3650),
             ('CP-003', 'Kendur Village Centre', 'V-002', 18.7810, 74.3110),
             ('CP-004', 'Kendur Crossing', 'V-002', 18.7760, 74.3050),
             ('CP-005', 'Pabal Main Road', 'V-003', 18.8910, 74.2610),
             ('CP-006', 'Nimgaon Temple', 'V-004', 18.7410, 74.4310),
             ('CP-007', 'Talegaon Square', 'V-005', 18.9110, 74.4410),
             ('CP-008', 'Talegaon East', 'V-005', 18.9150, 74.4500))
         as d(code, name, village_code, lat, lon)
         join village v on v.code = d.village_code
on conflict (code) do nothing;

-- Note CP-001 (three farmers) and CP-003, CP-004, CP-005, CP-006, CP-007 (two each): several
-- farmers share one collection point, which stays a single stop on the route.
insert into farmer (farmer_code, name, phone, village_id, collection_point_id,
                    expected_morning_quantity_litres, expected_evening_quantity_litres,
                    status, created_at, updated_at)
select d.code, d.name, d.phone, v.id, cp.id, d.morning, d.evening, 'ACTIVE', now(), now()
from (values ('F-0001', 'Ramesh Pawar', '9820100001', 'V-001', 'CP-001', 55.00, 45.00),
             ('F-0002', 'Sunita Jadhav', '9820100002', 'V-001', 'CP-001', 40.00, 35.00),
             ('F-0003', 'Ganesh More', '9820100003', 'V-001', 'CP-001', 62.50, 50.00),
             ('F-0004', 'Anita Kale', '9820100004', 'V-001', 'CP-002', 48.00, 40.00),
             ('F-0005', 'Vijay Shinde', '9820100005', 'V-001', 'CP-002', 51.50, 42.00),
             ('F-0006', 'Mangal Bhosale', '9820100006', 'V-002', 'CP-003', 70.00, 55.00),
             ('F-0007', 'Dattatray Gaikwad', '9820100007', 'V-002', 'CP-003', 44.00, 36.00),
             ('F-0008', 'Shobha Sawant', '9820100008', 'V-002', 'CP-004', 58.00, 47.00),
             ('F-0009', 'Prakash Deshmukh', '9820100009', 'V-002', 'CP-004', 39.50, 33.00),
             ('F-0010', 'Kavita Patil', '9820100010', 'V-003', 'CP-005', 66.00, 52.00),
             ('F-0011', 'Nitin Chavan', '9820100011', 'V-003', 'CP-005', 47.00, 38.00),
             ('F-0012', 'Sanjay Nikam', '9820100012', 'V-004', 'CP-006', 53.00, 44.00),
             ('F-0013', 'Ashwini Salunkhe', '9820100013', 'V-004', 'CP-006', 41.00, 34.00),
             ('F-0014', 'Balu Yadav', '9820100014', 'V-005', 'CP-007', 60.00, 49.00),
             ('F-0015', 'Rekha Thorat', '9820100015', 'V-005', 'CP-007', 45.50, 37.00),
             ('F-0016', 'Sachin Dhumal', '9820100016', 'V-005', 'CP-008', 57.00, 46.00))
         as d(code, name, phone, village_code, cp_code, morning, evening)
         join village v on v.code = d.village_code
         join collection_point cp on cp.code = d.cp_code
on conflict (farmer_code) do nothing;

-- Two routes, each with one published version. The shift is a property of a run, not of a
-- route, so either version can be run morning or evening; the names reflect how the dairy
-- currently uses them.
insert into route (route_code, name, status, created_at, updated_at)
select d.code, d.name, 'ACTIVE', now(), now()
from (values ('R-M01', 'Morning western loop'),
             ('R-E01', 'Evening eastern loop')) as d(code, name)
on conflict (route_code) do nothing;

insert into route_version (route_id, version_number, status, created_at, published_at)
select r.id, 1, 'PUBLISHED', now(), now()
from route r
where r.route_code in ('R-M01', 'R-E01')
on conflict (route_id, version_number) do nothing;

-- Stop order works outward first and finishes next to the plant, so the milk collected first
-- spends the least possible time on board. Planned times are the planner's intent; the
-- timetable a run is actually held to is projected when the run is created.
insert into route_stop (route_version_id, collection_point_id, sequence_number,
                        planned_arrival_time, planned_departure_time)
select rv.id, cp.id, d.seq, d.arrive::time, d.depart::time
from (values ('R-M01', 'CP-005', 1, '05:36', '05:43'),
             ('R-M01', 'CP-003', 2, '06:18', '06:25'),
             ('R-M01', 'CP-004', 3, '06:27', '06:34'),
             ('R-M01', 'CP-001', 4, '06:58', '07:07'),
             ('R-M01', 'CP-002', 5, '07:09', '07:16'),
             ('R-E01', 'CP-008', 1, '16:34', '16:39'),
             ('R-E01', 'CP-007', 2, '16:42', '16:49'),
             ('R-E01', 'CP-006', 3, '17:38', '17:45'))
         as d(route_code, cp_code, seq, arrive, depart)
         join route r on r.route_code = d.route_code
         join route_version rv on rv.route_id = r.id and rv.version_number = 1
         join collection_point cp on cp.code = d.cp_code
on conflict (route_version_id, sequence_number) do nothing;
