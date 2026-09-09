# Milk Before It Spoils

Backend for a dairy that collects milk twice a day from ~1,400 farmers across 60 villages using
22 tankers, and needs to stop losing loads to spoilage.

Java 21 · Spring Boot 3.4 · PostgreSQL 16 · Flyway · Docker Compose · 141 tests

---

## Problem Understanding

Four concrete problems are stated in the brief, and the design follows from them:

| The problem | What it actually means | How the system answers it |
|---|---|---|
| "Routes were drawn on a paper map by a manager who retired in 2019 and nobody has touched them since" | There is no versioned, machine-readable plan, and nobody dares change it because the current one is not written down | Routes are **versioned**. A published version is immutable, revising a route creates a new version, and every past run still points at the version it drove |
| "Milk that sits in a tanker too long before it reaches the chilling plant is rejected outright" | The binding constraint is **time from first collection to plant arrival**, not distance | Holding time is validated **before** a run is created, and re-checked at every collection against the *oldest* milk on board |
| "Some collection points serve two farmers" | A stop is a place; a collection is a transaction with a person. Conflating them either duplicates stops or loses a farmer | `RouteStop → CollectionPoint`, `MilkCollection → (RunStop, Farmer)`. Two farmers at CP-001 produce two collection records against **one** physical stop |
| "When a farmer calls to ask where the tanker is, nobody can tell him" | Position and progress are not recorded, so no estimate is possible | Tankers report positions; `GET /farmers/{id}/next-collection` answers with the run, the tanker, its last known position, an arrival estimate and how many stops come first |

The fifth problem is implicit and is the one that actually loses milk: **nobody checks whether a
plan is possible before the tanker leaves.** A route that cannot reach the plant within the
holding limit, or that expects more milk than the tanker holds, is rejected at run creation
rather than discovered at 7 a.m. with a full tanker.

## Solution Overview

The system has two sides, matching the two halves of the brief.

**Planning (design time).** Master data for the physical network, routes and immutable route
versions, and a greedy optimizer that proposes how to cover a set of collection points with a
fleet. Planning output is a *proposal*: it persists nothing, so turning it into a route version
stays an explicit human decision.

**Operations (twice a day).** A `CollectionRun` binds one published route version to one tanker
for one date and one shift. Creating it projects a timetable and validates feasibility. Driving
it moves a guarded state machine: start, arrive, collect, complete or skip, close out. Every
milk collection is checked against tanker capacity and remaining holding time before it is
accepted.

Both sides share one projection engine, so the schedule that approved a run, the holding-time
decision taken at intake, and the ETA quoted to a farmer can never disagree about the arithmetic.

## Architecture

A modular monolith: one deployable, one database, one transaction manager.

```
                                  REST  /api/v1
                                       │
              ┌────────────────────────┴────────────────────────┐
              │                                                 │
      PLANNING (design time)                          OPERATIONS (twice a day)
              │                                                 │
  village · farmer · collectionpoint              run    CollectionRun, RunStop
  tanker  · chillingplant                         collection   MilkCollection
  route   Route, RouteVersion, RouteStop          tracking     TankerLocation, ETA
              │                                                 │
      RouteOptimizer  (replaceable)                    RunStateMachine
      RouteFeasibilityService                          EtaService
              │                                                 │
              └────────────────────────┬────────────────────────┘
                                       │
                      ScheduleProjector  +  TravelTimeProvider
                            (one shared projection engine)
                                       │
                              Spring Data JPA
                                       │
                          PostgreSQL 16  +  Flyway
```

Packages are feature-first (`village`, `farmer`, `collectionpoint`, `tanker`, `chillingplant`,
`route`, `run`, `collection`, `tracking`, plus `common`, `config`, `exception`), each with its own
`controller / service / repository / entity / dto`. Controllers are thin, entities never cross the
API boundary, and `spring.jpa.open-in-view=false` forces every entity-to-DTO mapping to happen
inside its transaction.

## Domain Model

```
village ──1:N──> collection_point ──1:N──> farmer
   └────1:N────> farmer                        │
                                               │
route ──1:N──> route_version ──1:N──> route_stop ──> collection_point
                     │                        ▲
                     │                        │
                     └──1:N──> collection_run │       tanker ──┐
                                    │  │      │       chilling_plant ──┐
                                    │  └──────┼───────────────────────┴──> (run context)
                                    │         │
                                    ├──1:N──> run_stop ──────────────┘
                                    │             └──1:N──> milk_collection ──> farmer
                                    └──1:N──> tanker_location ──> tanker
```

Twelve tables. The relationships that carry the design:

- **`collection_point` and `farmer` are separate entities, 1:N.** A collection point is a place
  with coordinates; a farmer is a person with a phone number, a payment identity and a per-shift
  expected volume. The brief's "some collection points serve two farmers" is then just data, and
  service time at a stop grows with the number of farmers there.
- **`collection_run → route_version`, never `→ route`.** This is what makes history reproducible.
  Publishing a revised version archives the old one; a run created last March still resolves to
  the exact ordered stop list it drove.
- **`milk_collection → (run_stop, farmer)`** with a unique constraint on the pair. One stop, many
  collections, at most one per farmer.
- **`route_stop.planned_arrival_time` is `LocalTime`, `run_stop.planned_arrival_time` is
  `Instant`.** A route stop is a template ("around 05:40"); a run stop is a real event on a real
  date. The types say so.

## Planning Flow

```
POST /routes                                   create the logical route
POST /routes/{id}/versions                     open a DRAFT (optionally copying an earlier version)
POST /routes/{id}/versions/{vid}/stops         add stops - only while DRAFT
POST /routes/{id}/versions/{vid}/publish       validate, archive the previous plan, freeze this one
POST /planning/optimize                        propose routes for a fleet and shift (persists nothing)
```

Publishing validates that the version has stops, that every collection point is active, and that
sequence numbers are exactly `1..N` — a published stop order is a driving order, so gaps or
duplicates would leave it open to interpretation in the field.

**Feasibility is deliberately not checked at publish.** Capacity and holding time both depend on
*which tanker* drives the route and *which plant* it delivers to, and those are facts about a run,
not about a route. They are enforced when a run is created.

### The optimizer

`GreedyRouteOptimizer`, one tanker at a time, largest capacity first:

1. **Seed** with the unassigned collection point *farthest* from the plant. This is the one
   deliberate insight in the heuristic: holding time is measured from the *first* collection, so a
   route should pick up the remote milk first and finish next to the plant. Seeding with the
   nearest point does the opposite and spends holding time on the final leg.
2. **Extend** by repeatedly appending the nearest remaining point that keeps the load within
   capacity *and* the projected holding time within the limit.
3. **Close** the route when nothing can be appended, and move to the next tanker.

Anything the fleet cannot cover comes back in `unassigned` **with a reason** — oversized for the
largest tanker, unreachable within the holding limit even as a solo stop, or simply out of fleet.
Nothing is silently dropped. Ties break on collection point id, so the same input always yields
the same plan, which is what makes it testable.

## Running Flow

```
POST /runs                                  plan a run: published version + tanker + date + shift
POST /runs/{id}/start                       the tanker leaves the plant
POST /tankers/{id}/location                 position pings (attached to the active run automatically)
POST /runs/{id}/stops/{sid}/arrive          arrival at a collection point
POST /runs/{id}/stops/{sid}/collections     milk from one farmer  (repeat per farmer at the stop)
POST /runs/{id}/stops/{sid}/complete        departure from the stop
POST /runs/{id}/stops/{sid}/skip            nobody was there / point unreachable
POST /runs/{id}/complete                    delivered to the plant
POST /runs/{id}/cancel                      only while PLANNED
```

Run states, with everything else rejected as `INVALID_RUN_STATE`:

```
PLANNED ──start──> STARTED ──first arrival──> IN_PROGRESS ──complete──> COMPLETED
   │                  │                                                      ▲
   │                  └──────────────────────────────────────────────────────┘
   │                         (a run whose every stop was skipped)
   └──cancel──> CANCELLED
```

Stop states:

```
PENDING ──arrive──> ARRIVED ──first collection──> COLLECTING ──complete──> COMPLETED
   │                   │                                                       ▲
   │                   └───────────────────────────────────────────────────────┘
   └──skip──> SKIPPED
```

Two details that matter operationally:

- **A run cannot be closed while any stop is unfinished** (`INVALID_STOP_STATE`). Leaving stops
  pending is exactly how farmers get silently dropped, which is the failure mode the dairy already
  has. Complete them or skip them explicitly.
- **A stop cannot be skipped after arrival.** Skipping is a pre-arrival decision, so it can never
  hide milk that was already collected.

## Business Rules

### 1. Tanker capacity

The total collected in a run may never exceed the tanker's capacity. Enforced server-side, from
the recorded collections — never from anything the client sends.

```
Capacity 5,000 L, already on board 4,950 L, offered 100 L
  → 409 TANKER_CAPACITY_EXCEEDED
    "Collecting 100.00 L would exceed tanker TNK-01: 4950.00 L of 5000.00 L already
     on board, 50.00 L remaining"
```

Litres are `BigDecimal` throughout (`numeric(10,2)`), because they are summed and then compared
against a hard limit; binary floating point would misbehave exactly at the boundary. Filling to
capacity *exactly* is allowed — the bound is inclusive, and there is a test for it.

### 2. Concurrent collections

**Two different concurrency problems, two appropriate mechanisms.**

*Capacity accumulation* is a read-modify-write over aggregate state, so recording milk opens a
transaction with a **`PESSIMISTIC_WRITE` lock on the `collection_run` row** and only then sums the
litres. Two field devices posting at the same instant are serialised by the database: the second
sees the first one's litres and is rejected. At 22 tankers and roughly one collection per minute
each, serialising per run costs nothing.

```
Remaining capacity 100 L.  Request A = 80 L,  Request B = 60 L
  → exactly one succeeds, the other gets 409 TANKER_CAPACITY_EXCEEDED
```

`ConcurrentCollectionIT` asserts this from two threads. It is a test with teeth: replacing
`findByIdForUpdate` with a plain `findById` makes it fail with both collections accepted and the
tanker overfilled.

*Run state transitions* are a lost-update on a single row, so `CollectionRun` carries a JPA
`@Version` and two dispatchers acting at once get `409 CONCURRENT_MODIFICATION` rather than one
silently overwriting the other.

Java `synchronized` is used nowhere — it would not survive a second instance of the service.

### 3. Milk holding time

> **Assumption.** The assignment does not specify a maximum holding time. The MVP uses **4 hours**
> as a configurable example (`milk.max-holding-duration=PT4H`). This is a placeholder for
> demonstration, **not** a real dairy regulation or a figure from the brief.

The rule is applied at two points.

**Before execution (the important one).** Creating a run projects the whole timetable and checks

```
plantArrivalTime − arrivalAtFirstStop  ≤  milk.max-holding-duration
```

If it fails, the run is refused with `MILK_HOLDING_TIME_EXCEEDED` and nothing is persisted. The
manager must split the route, assign a different tanker, or reorder the stops — which is precisely
the conversation the 2019 paper map has been preventing.

**At intake (the safety net).** A run that has fallen behind schedule can still go bad, so every
collection re-projects the remaining route from where the tanker actually is and checks

```
projectedPlantArrival − oldestMilkOnBoard  ≤  milk.max-holding-duration
```

The limit applies to the **oldest** milk in the tanker, because that is what decides whether the
whole load is rejected at the plant. For the first collection of a run the oldest milk *is* the
milk being offered, so one formula covers both cases.

**Chosen behaviour on breach: the collection is refused (`409`) and nothing is persisted.**
Rejecting at intake leaves the milk with the farmer, who can still do something with it, instead
of adding it to a load that is already lost. `MilkHoldingTimeIT` drives an injected clock forward
three hours to reproduce a late tanker and assert this.

### 4. Route versioning

Published versions are immutable. Adding stops to one is `ROUTE_VERSION_IMMUTABLE`; revising a
route means creating a new version, which may copy the previous version's stops so nobody retypes
eight of them. Publishing archives the previously published version, and a **partial unique index**
(`unique (route_id) where status = 'PUBLISHED'`) makes "at most one plan of record per route" a
database guarantee rather than a promise from the service layer.

### 5. Multiple farmers per collection point

One `RunStop`, many `MilkCollection` rows:

```
CP-001  (one physical stop, sequence 4)
  ├── F-0001  Ramesh Pawar   58.50 L
  └── F-0002  Sunita Jadhav  41.00 L
```

A farmer may only be collected at their own collection point — anything else is
`FARMER_NOT_ASSIGNED_TO_STOP`, because accepting it would corrupt the payment record. And the
farmers still queueing at the current stop are counted into the ETA for everyone behind them.

### 6. Duplicate collection

**Chosen behaviour: one collection per `(run_stop, farmer)`.** The service checks first for a clear
message (`DUPLICATE_COLLECTION`), and a unique constraint on `(run_stop_id, farmer_id)` makes a
double-tap on a field device impossible. Because all collections for a run serialise behind the
run's row lock, a concurrent duplicate hits the service check too, not just the constraint.

### 7. No overlapping runs per tanker

A tanker may not be booked twice for the same date and shift — enforced by a **partial unique
index** `(tanker_id, run_date, shift) where status <> 'CANCELLED'`, so cancelling a run releases
the slot. It also may not have two runs *on the road* at once, checked when a run starts.

### 8. Farmer ETA

`GET /api/v1/farmers/{id}/next-collection` returns the assigned collection point, the run and
tanker serving it today, the run status, the tanker's last reported position, an estimated arrival,
and the number of stops before theirs — in one of six states: `NO_RUN_SCHEDULED`, `SCHEDULED`,
`EN_ROUTE`, `AT_YOUR_COLLECTION_POINT`, `COLLECTED`, `SKIPPED`.

The estimate is built from the run's **actual progress**, not its original plan: the last reported
GPS position, or failing that the last stop with a *recorded arrival*. (A stop skipped out of order
has no arrival time and so cannot be mistaken for progress — there is a test for that.)

Every response carries an `estimateBasis` string stating that the figure is geometric and uses no
live traffic data. That honesty is deliberate: it is a projection, not a prediction.

## API Documentation

Swagger UI at `http://localhost:8080/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`.
39 endpoints across 29 paths.

| Area | Endpoints |
|---|---|
| Villages | `POST/GET /api/v1/villages`, `GET /api/v1/villages/{id}` |
| Collection points | `POST/GET /api/v1/collection-points` (`?villageId=`), `GET /{id}` (includes `farmerCount`) |
| Farmers | `POST/GET /api/v1/farmers` (`?villageId=&collectionPointId=&phone=`), `GET /{id}` |
| Tankers | `POST/GET /api/v1/tankers`, `GET /{id}` |
| Chilling plants | `POST/GET /api/v1/chilling-plants`, `GET /{id}` |
| Routes | `POST/GET /api/v1/routes`, `GET /{id}` |
| Route versions | `POST/GET /api/v1/routes/{id}/versions`, `GET /versions/{vid}`, `POST .../stops`, `POST .../publish` |
| Planning | `POST /api/v1/planning/optimize` |
| Runs | `POST/GET /api/v1/runs` (`?runDate=&shift=&status=&tankerId=`), `GET /{id}`, `POST /{id}/start\|complete\|cancel` |
| Run stops | `POST /api/v1/runs/{id}/stops/{sid}/arrive\|complete\|skip` |
| Milk collection | `POST/GET /api/v1/runs/{id}/stops/{sid}/collections`, `GET /api/v1/runs/{id}/collections` |
| Tracking | `POST/GET /api/v1/tankers/{id}/location`, `GET /api/v1/farmers/{id}/next-collection` |
| Health | `GET /actuator/health` |

List endpoints are paged with a stable default sort (paging without a total order can repeat or
skip rows across pages).

### Errors

One shape for every failure, with a stable machine-readable `code`:

```json
{
  "timestamp": "2026-09-09T14:34:02.092739Z",
  "status": 409,
  "code": "DUPLICATE_COLLECTION",
  "message": "Farmer F-0001 already has a collection recorded at stop 4 of run RUN-20260909-M-00001",
  "path": "/api/v1/runs/1/stops/4/collections"
}
```

`RESOURCE_NOT_FOUND` · `VALIDATION_FAILED` (adds a `fieldErrors` array) · `MALFORMED_REQUEST` ·
`DUPLICATE_RESOURCE` · `CONSTRAINT_VIOLATION` · `CONCURRENT_MODIFICATION` · `INVALID_ROUTE` ·
`ROUTE_VERSION_NOT_PUBLISHED` · `ROUTE_VERSION_IMMUTABLE` · `NO_TANKERS_AVAILABLE` ·
`INVALID_RUN_STATE` · `INVALID_STOP_STATE` · `TANKER_ALREADY_ASSIGNED` ·
`TANKER_CAPACITY_EXCEEDED` · `MILK_HOLDING_TIME_EXCEEDED` · `FARMER_NOT_ASSIGNED_TO_STOP` ·
`DUPLICATE_COLLECTION` · `INACTIVE_RESOURCE` · `INTERNAL_ERROR`

This is the complete list of codes the system actually returns — the enum carries no constants
that no code path can produce.

Stack traces and SQL details are logged, never returned.

## Running Locally

Requires **Java 21** and **Maven 3.9+**. Docker is needed for PostgreSQL and for the integration
tests.

```bash
# 1. PostgreSQL only
docker compose up -d

# 2. The application, with the demo dataset loaded
mvn spring-boot:run -Dspring-boot.run.profiles=demo

# Swagger UI      http://localhost:8080/swagger-ui.html
# Health          http://localhost:8080/actuator/health
```

Without `-Dspring-boot.run.profiles=demo` the schema is created but no data is seeded.

Configuration is entirely environment variables — copy `.env.example` to `.env` and adjust.
Every variable has a built-in default, so both compose and the application start with no `.env`
present. No credentials are committed.

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | `localhost` `5432` `milk_collection` `milk` `milk` | Database connection |
| `SERVER_PORT` | `8080` | HTTP port |
| `APP_TIME_ZONE` | `Asia/Kolkata` | Zone for local dates and times |
| `MILK_MAX_HOLDING_DURATION` | `PT4H` | Holding limit (an assumption — see above) |
| `ROUTING_AVERAGE_SPEED_KMPH` | `30` | Travel-time model |
| `ROUTING_ROAD_WINDING_FACTOR` | `1.3` | Straight-line to road distance |
| `ROUTING_STOP_BASE_SERVICE` / `ROUTING_PER_FARMER_SERVICE` | `PT3M` / `PT2M` | Service time per stop and per farmer |
| `OPERATIONS_MORNING_START` / `OPERATIONS_EVENING_START` | `05:00` / `16:00` | Default shift departure times |

## Running Tests

```bash
mvn clean test      # 76 unit tests - no database, no Docker, ~5 seconds
mvn clean verify    # the above plus 65 integration tests (needs Docker)
```

Unit tests (`*Test`, Surefire) and integration tests (`*IT`, Failsafe) are split deliberately, so
the fast suite runs anywhere while the integration suite exercises real PostgreSQL.

**Unit tests** cover the components that are pure functions of their inputs, which is why they were
designed that way: `GreedyRouteOptimizer`, `ScheduleProjector`, `RouteFeasibilityService`,
`RunStateMachine` (the full transition table, including every transition the brief calls invalid),
`MilkCollectionService` (capacity and holding-time boundaries with Mockito), `Coordinates`,
`SimpleTravelTimeProvider`.

**Integration tests** run against a real PostgreSQL 16 via Testcontainers, with the real Flyway
migrations and the real HTTP layer. They are not `@Transactional`: capacity accumulation, duplicate
protection and the concurrency test all depend on data actually being committed, so isolation comes
from truncating the schema before each test instead.

| Suite | Covers |
|---|---|
| `MasterDataIT` | two farmers sharing a collection point, phone lookup, duplicate codes, field validation |
| `RouteVersioningIT` | publish, immutability, archiving, duplicate points, non-consecutive sequences |
| `PlanningOptimizeIT` | demand aggregation, farthest-first ordering, unassigned reasons, per-shift volumes |
| `RunLifecycleIT` | the full lifecycle plus every invalid transition, tanker double-booking, infeasible runs |
| `MilkCollectionIT` | two farmers at one stop, duplicates, wrong collection point, capacity overflow |
| `MilkHoldingTimeIT` | holding time with the clock driven forward to simulate a late tanker |
| `ConcurrentCollectionIT` | two threads against one tanker's capacity |
| `TankerTrackingIT` | append-only history, latest position, automatic run attachment |
| `FarmerEtaIT` | all six ETA states, with exact arrival instants derived from the documented model |
| `SeedDataIT` | the demo dataset loads, is idempotent, and its routes are actually drivable |
| `FullCollectionJourneyIT` | the whole sequence a dairy performs, empty database to closed run |

Time-dependent behaviour is testable because `java.time.Clock` is a bean rather than a call to
`Instant.now()`. `MilkHoldingTimeIT` and `FarmerEtaIT` replace it and assert exact instants, so the
arithmetic of the holding-time and ETA calculations is itself under test — not merely its shape.

### What was actually executed

- `mvn clean test` — 76 unit tests, green.
- The integration suite — 65 tests, green, against a real PostgreSQL 16 (all five migrations plus
  the seed applied by Flyway, `ddl-auto=validate` accepting the entity mappings).
- The packaged jar booted against a real PostgreSQL 16 with the `demo` profile, configured purely
  through `DB_*` environment variables: health `UP`, all migrations applied, Swagger UI served, and
  the sample flow below driven end to end with `curl`.

## Docker

```bash
docker compose up -d                          # PostgreSQL only (for running the app with Maven)
docker compose --profile app up -d --build    # PostgreSQL + the application, both in containers
docker compose logs -f app
docker compose down -v                        # stop and delete the volume
```

The application sits behind a compose profile so that `docker compose up -d` leaves port 8080 free
for `mvn spring-boot:run`. The image is a multi-stage build (Maven for the build, a JRE for the
runtime) and runs as a non-root user. The app container waits on the database's `pg_isready`
healthcheck, and the containerised app runs with the `demo` profile by default.

## Sample API Flow

Real output from the running application against the seeded dataset (ids will differ).

```bash
API=http://localhost:8080/api/v1
```

**1. Ask the optimizer for a plan.**

```bash
curl -s -X POST $API/planning/optimize -H 'Content-Type: application/json' \
  -d '{"chillingPlantId":1,"shift":"MORNING"}'
```

```
assumptions: maxMilkHoldingDuration PT4H, averageSpeedKmph 30.0, roadWindingFactor 1.3,
             stopBaseServiceDuration PT3M, perFarmerServiceDuration PT2M
summary:     8 collection points considered, 8 assigned, 0 unassigned,
             2 tankers available, 1 used, 16 farmers covered, 838.0 L expected
route:       TNK-01  838.0 L  16.8% full  holding PT3H56M22S
             CP-005 → CP-002 → CP-001 → CP-003 → CP-004 → CP-006 → CP-007 → CP-008
```

Note the plan starts at CP-005, the point farthest from the plant, and that holding time
(3h56m) is what binds — not capacity (16.8%). See *Trade-offs* for what this output reveals about
the heuristic.

**2. Create and start a run against the published plan.**

```bash
curl -s -X POST $API/runs -H 'Content-Type: application/json' \
  -d '{"routeVersionId":1,"tankerId":1,"chillingPlantId":1,"runDate":"2026-09-09","shift":"MORNING"}'
curl -s -X POST $API/runs/1/start
```

```
run RUN-20260909-M-00001 STARTED
stops (1, CP-005, 2026-09-09T00:06:21Z) (2, CP-003, …T00:47:58Z) (3, CP-004, …T00:57:09Z)
      (4, CP-001, …T01:27:50Z) (5, CP-002, …T01:39:01Z)
```

**3. Report a position — it attaches to the active run by itself.**

```bash
curl -s -X POST $API/tankers/1/location -H 'Content-Type: application/json' \
  -d '{"latitude":18.86,"longitude":74.28}'
# → fix attached to run RUN-20260909-M-00001
```

**4. A farmer rings up.**

```bash
curl -s $API/farmers/3/next-collection
```

```
EN_ROUTE
"Tanker TNK-01 is on the road with 3 stop(s) before yours and should reach CP-001 around 21:35."
stopsBefore 3 | eta 2026-09-09T16:05:52Z | in PT1H31M51S | lastFix 18.86, 74.28
```

**5. Arrive, then collect from both farmers at the shared stop.**

```bash
curl -s -X POST $API/runs/1/stops/4/arrive
curl -s -X POST $API/runs/1/stops/4/collections -H 'Content-Type: application/json' \
  -d '{"farmerId":3,"quantityLitres":58.5}'
curl -s -X POST $API/runs/1/stops/4/collections -H 'Content-Type: application/json' \
  -d '{"farmerId":4,"quantityLitres":41.0}'
```

```
A: F-0001 58.5 L | load 58.5/5000.0 | holding PT16M55S, PT3H43M5S remaining
B: F-0002 41.0 L | load 99.5
```

**6. A double-tap is refused.**

```bash
curl -s -X POST $API/runs/1/stops/4/collections -H 'Content-Type: application/json' \
  -d '{"farmerId":3,"quantityLitres":10}'
```

```json
{ "status": 409, "code": "DUPLICATE_COLLECTION",
  "message": "Farmer F-0001 already has a collection recorded at stop 4 of run RUN-20260909-M-00001" }
```

**7. One stop, two collections.**

```bash
curl -s $API/runs/1/stops/4/collections
# → [("F-0001", 58.5), ("F-0002", 41.0)]
```

Then `POST /runs/1/stops/4/complete`, work the remaining stops, and `POST /runs/1/complete`.

## Technology Choices

| Choice | Why |
|---|---|
| **Java 21** | Required. Records make the DTO and value-object layer terse and immutable; exhaustive `switch` expressions make the ETA state mapping total over the enum. |
| **Spring Boot 3.4** | Declarative transactions, Bean Validation, `@RestControllerAdvice`, Actuator and externalised configuration are all things this problem genuinely needs, and none of them are worth hand-rolling. |
| **PostgreSQL** | The correctness of this system rests on constraints the database can enforce: **partial unique indexes** (one published version per route; one tanker per date and shift unless cancelled), `SELECT … FOR UPDATE` for capacity, and check constraints. Postgres does all of it well, and the data is unambiguously relational. |
| **Spring Data JPA** | Mostly aggregate-shaped reads and writes with a handful of tuned queries. Entity graphs and fetch joins handle the N+1 risk; `Specification` handles optional filters. Plain SQL/jOOQ would trade away the mapping I want for query control I do not need at this scale. |
| **Flyway** | The schema is the contract. Versioned migrations with `ddl-auto=validate` means the application refuses to start if entities and schema have drifted — a real check, not a comment. |
| **Testcontainers** | Partial unique indexes, `FOR UPDATE` and `numeric` semantics do not exist on H2. Testing against anything other than the real engine would test something other than what ships. |
| **Maven** | Boot's parent POM and the Surefire/Failsafe split are conventional and need no build code. |
| **Lombok** | Only `@Getter/@Setter/@Builder` on entities and `@RequiredArgsConstructor`/`@Slf4j` on services. DTOs are records, so Lombok is nowhere near the API surface. |
| **springdoc-openapi** | Generated from the code, so it cannot drift from the endpoints. |

Deliberately **not** used: microservices, Kafka, Redis, Elasticsearch, WebSockets, Kubernetes,
cloud services, external map APIs. None of them solve a problem this brief poses, and each would
have to be defended in an interview on merits it does not have. PostgreSQL and Spring Boot are
sufficient at 22 tankers and ~1,400 farmers, and the reasoning for that is in *How this scales*.

## Assumptions

Everything in this section is a **decision I made**, not something the brief specified. The brief
gave the domain, the Java constraint and the shape of the two sides of the system; nothing below
was stated in it.

1. **Maximum milk holding time is 4 hours.** The brief says milk "sitting too long" is rejected but
   gives no threshold. `milk.max-holding-duration=PT4H` is a configurable placeholder for
   demonstration — **not** a dairy regulation, and not a number from the brief. It is also the
   safety-margin knob: a planner who wants slack sets it to `PT3H30M`.
2. **Holding time is measured from arrival at the first stop to arrival at the chilling plant** —
   the age of the oldest milk in the tanker.
3. **Travel time = straight-line distance × 1.3 ÷ 30 km/h**, all three configurable. No traffic, no
   map API, no road network. Deterministic by design so that feasibility and ETA are testable.
4. **Service time at a stop = 3 minutes + 2 minutes per farmer.**
5. **A single timezone, `Asia/Kolkata`.** `run_date` is a local calendar date; local shift times are
   resolved against this zone exactly once, and everything downstream is `Instant`.
6. **Milk refused at intake is not persisted.** A rejected collection returns 409 and writes
   nothing, so `milk_collection.status` only ever holds `ACCEPTED`. The column exists because a
   persisted refusal or correction trail is the obvious next requirement, and adding one should be
   a data change rather than a schema redesign.
7. **One collection per farmer per stop.** No correction or void flow — a wrong quantity currently
   needs a database fix. This is the assumption I would revisit first.
8. **Cancellation only from `PLANNED`.** Once milk is on board a run is closed out, not erased.
9. **A run cannot be closed with unfinished stops.** Each must be completed or explicitly skipped.
10. **`collectedAt` and position timestamps are server-assigned.** A device that chose its own
    timestamp could backdate milk past the holding check. The cost is that genuinely offline,
    buffered pings are not supported.
11. **A farmer's village and their collection point are not constrained to match** — a farmer may
    live in one village and deliver at a point in a neighbouring one. Only the collection point
    determines where milk is picked up.
12. **`status` may be supplied when creating master data** (defaulting to `ACTIVE`). Without it,
    rules like "an inactive collection point cannot be routed" would be unreachable through the API,
    since the brief's endpoint list has no update operations.
13. **The ETA endpoint considers today only**, in the configured zone.
14. **No authentication or authorisation.** See below.

## What Was Left Out

Each of these was a decision, not an oversight.

- **A full VRP optimizer.** *A full Vehicle Routing Problem optimizer is intentionally outside the
  MVP scope. The optimizer abstraction allows a more advanced optimization engine to be introduced
  later.* Concretely: `RouteOptimizer` is a one-method interface taking plain value objects, so
  swapping `GreedyRouteOptimizer` for OR-Tools or Timefold is a single bean change that touches no
  operations code. Writing a branch-and-cut solver in 48 hours would produce something worse than
  the heuristic and impossible to defend line by line.
- **Live traffic and external routing.** `TravelTimeProvider` is the seam. A real implementation
  (OSRM, a cached distance matrix, Google Directions) drops in behind it. Quoting a traffic-aware
  ETA without traffic data would be a lie, which is why every estimate is labelled with its basis.
- **Real GPS hardware.** The system accepts position reports over HTTP, which is what a telematics
  box or a driver's phone would send anyway. Device provisioning, buffering and duplicate handling
  are integration work, not domain work.
- **WebSocket live tracking.** The farmer-facing question is answered by one request. Push would add
  a stateful transport, connection management and a fan-out story to save a poll — worth it once
  there is a live dispatcher map, not before.
- **SMS/WhatsApp notifications.** The obvious next feature ("your tanker is 20 minutes away") and a
  natural first use of an outbox, but it is a delivery concern with a provider contract attached,
  and the brief asks for a backend.
- **Authentication and authorisation.** Genuinely omitted. Production needs at least three roles —
  planner, driver, farmer — and a farmer must not be able to read another farmer's collections;
  `GET /farmers/{id}/next-collection` is unauthenticated and would need the identity of the caller
  bound to the farmer. This is the largest gap between this MVP and something deployable, and I
  would rather state that plainly than ship a token check that looks like security.
- **Cloud deployment, Kubernetes, multi-region.** The artefact is a jar and a compose file. Nothing
  in the design resists being deployed; there is no reason to guess at somebody's infrastructure.
- **Advanced analytics.** Yield trends, per-farmer quality history and rejection analysis are
  reporting on data this schema already captures, and would be built as read models rather than by
  changing the write path.
- **A mobile application.** Out of scope for a backend assignment.

## Trade-offs

**The greedy heuristic optimises for fewest vehicles, not for safety margin.** The sample output
above is honest about this: it packs all eight collection points onto one tanker at 16.8% capacity
with a holding time of 3h56m against a 4h limit. That is *feasible* by the stated rule and uses one
tanker instead of two, but no dispatcher would want four minutes of slack. Two things follow: the
configurable limit is the margin control (set `PT3H30M` and the optimizer splits the work), and a
real objective function balancing vehicle count against risk is exactly the kind of thing a proper
VRP solver expresses and a greedy heuristic cannot.

**Pessimistic locking on the run row serialises collections per run.** Correct and simple, at the
cost of concurrency within a single run. At the real volumes — 22 tankers, roughly 60 collections
per run over several hours — the contention is nil, and I would rather explain a lock than a
retry loop. If a run ever needed genuine parallel intake, the move is a denormalised
`collected_litres` column updated with a conditional `UPDATE … WHERE collected + ? <= capacity`,
which is lock-free but harder to reason about.

**Capacity is a `SUM` over child rows rather than a denormalised counter.** One query per
collection, no drift, no second source of truth. Under the lock, a counter would be equally
correct — but it would be a number that could disagree with the collections it summarises.

**`RunStop` planned times are projected, not copied from the route template.** `RouteStop` carries
the planner's intended times, but a run's timetable is projected with the same model the
feasibility check uses. One code path, and the schedule can never contradict the approval that
allowed the run.

**The read side is split from the write side for runs** (`RunQueryService` vs
`CollectionRunService`/`RunExecutionService`) purely to keep dependency lists honest — run creation
needs the whole planning stack, reads need three repositories. It is not CQRS and there is no
second model.

**Optional filters use `Specification`, not JPQL null checks.** The obvious
`where (:runDate is null or r.runDate = :runDate)` fails on PostgreSQL with *could not determine
data type of parameter* for an untyped null, which I hit and debugged. Specifications also mean an
absent filter contributes no SQL at all, rather than a predicate the planner has to see through.

**Some duplication was chosen over abstraction.** `CollectionPointDemand` and `PlannedStop` are
similar records with different lifecycles; the five master-data slices repeat a create/list/get
shape. A generic `BaseService<T>` would remove keystrokes and add a layer nobody can debug.

## Future Improvements

Roughly in the order I would actually do them.

1. **Authentication and role-based authorisation**, with a farmer scoped to their own data. The
   largest gap to production.
2. **A correction and void flow for collections**, with an audit trail. Field data entry is wrong
   sometimes, and today that means editing the database.
3. **Real travel times** behind `TravelTimeProvider` — most cheaply as a precomputed distance
   matrix over the ~60 collection points and one plant, refreshed nightly. That alone would sharpen
   both feasibility and ETA more than any change to the optimizer.
4. **A proper VRP solver** behind `RouteOptimizer` (OR-Tools or Timefold), with an objective that
   trades vehicle count against holding-time margin, plus per-farmer time windows.
5. **Notifications** ("your tanker is 20 minutes away"), written through a transactional outbox so a
   failed send never rolls back a collection.
6. **Route version diffing** — showing a planner exactly what changed between version 3 and
   version 4 — which the immutable-version model already makes possible.
7. **Retention and partitioning for `tanker_location`.** At 22 tankers pinging every 30 seconds
   this is ~2 million rows a month; monthly partitions plus a retention policy keep it flat.
8. **Reconciliation against plant weighbridge readings**, to close the loop between what was
   collected and what arrived.
9. **Idempotency keys** on collection and position endpoints, so a field device with a flaky
   connection can retry safely instead of relying on the duplicate check.
10. **Structured JSON logging with correlation ids**, and metrics on the operational events the
    system already logs.

## How This Scales

The stated load is not large, and it is worth being concrete rather than reflexively distributed.

**Writes.** 1,400 farmers × 2 shifts ≈ 2,800 collections a day, spread over two ~3-hour windows —
under 1 write per second at peak. Position pings from 22 tankers every 30 seconds is under 1/s.
A single Postgres instance is three orders of magnitude away from caring.

**Reads.** The heavy path is farmers checking their ETA. Each call is a handful of indexed queries;
if it ever became the bottleneck, the answer changes at most once per position ping, so it is
trivially cacheable per collection point.

**Data volume.** Everything except `tanker_location` grows by thousands of rows a day. Position
history is the only table with a real growth story, and partitioning plus retention handles it.

**What would actually change first**: adding a second application instance (the design is already
stateless, and correctness lives in database constraints and row locks rather than in application
memory — which is why `synchronized` appears nowhere), then a read replica for reporting, then
partitioning position history. Kafka and Redis would earn their place if the dairy grew to hundreds
of tankers with real-time fan-out; at 22 they would be complexity charged against no benefit.
