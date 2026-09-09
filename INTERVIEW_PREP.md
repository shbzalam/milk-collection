# Interview Preparation

Answers for the walkthrough. Everything here describes **what is actually implemented** unless a
line explicitly says "not implemented" or "would". Where the brief left something open, the answer
says so rather than presenting a choice as a requirement.

---

## 1. Two-minute project explanation

> A dairy collects milk twice a day from about 1,400 farmers in 60 villages using 22 tankers. Milk
> that sits in a tanker too long before reaching the chilling plant is rejected outright. The routes
> were drawn on a paper map by a manager who retired in 2019, nobody has changed them since, and
> when a farmer rings up to ask where the tanker is, nobody can tell him.
>
> I built a Spring Boot backend with two sides. **Planning** holds the physical network — villages,
> collection points, farmers, tankers, the chilling plant — plus routes that are *versioned*: a
> published route version is immutable, revising a route creates a new version, and every historical
> run still points at the exact version it drove. That directly addresses the paper-map problem:
> the plan can finally be changed without losing what it used to be.
>
> **Operations** is the twice-a-day execution. A collection run binds one published route version to
> one tanker for one date and shift. Creating it projects a timetable and refuses the run if the
> milk could not reach the plant within the holding limit, or if the expected volume exceeds the
> tanker. During the run, arrivals and milk collections are recorded, and every collection is
> checked against capacity and remaining holding time before it is accepted.
>
> Two design points I would highlight. First, collection points and farmers are separate entities
> with a one-to-many relationship, because the brief says some points serve two farmers — so two
> farmers at CP-001 produce two collection records against one physical stop. Second, tanker
> capacity is enforced under a pessimistic row lock on the run, so two field devices posting
> simultaneously cannot overfill a tanker between them. There is a test that fails if you remove
> the lock.
>
> Java 21, Spring Boot 3.4, PostgreSQL, Flyway, Docker Compose, 149 tests. The interesting business
> rules are unit tested, and the integration tests run against real PostgreSQL because the
> correctness of this system depends on constraints — partial unique indexes and `SELECT … FOR
> UPDATE` — that only the real engine has.

## 2. Five-minute architecture explanation

**Shape.** A modular monolith: one deployable, one database, one transaction manager. Packages are
feature-first — `village`, `farmer`, `collectionpoint`, `tanker`, `chillingplant`, `route`, `run`,
`collection`, `tracking`, plus `common`, `config`, `exception` — each with its own
`controller / service / repository / entity / dto`.

**Layering rules I held to.** Controllers are thin: validate, delegate, return a DTO. Business logic
lives in services and domain components. Entities never cross the API boundary, and
`spring.jpa.open-in-view=false` forces every entity-to-DTO mapping to happen inside its
transaction, which is why a lazy-loading mistake surfaces as a test failure rather than a
production surprise.

**The two sides and what they share.**

```
        PLANNING                              OPERATIONS
  route versions, optimizer            runs, stops, collections, tracking
  RouteFeasibilityService              RunStateMachine, EtaService
        └──────────────┬──────────────────────────┘
              ScheduleProjector + TravelTimeProvider
```

`ScheduleProjector` is the piece I would point at. It answers one question — given a position, a
moment and a list of remaining stops, when does the tanker reach each of them and then the plant —
and it has exactly three callers: the feasibility check that approves a run, the holding-time
decision taken when milk is offered, and the ETA quoted to a farmer. Because they share it, the
schedule that approved a run and the ETA a farmer is told can never be based on different
arithmetic. That was a refactor: projection started inside `RouteFeasibilityService` and moved out
once the second caller appeared.

**Two seams that are deliberately replaceable.** `TravelTimeProvider` (one method, coordinates to
duration) and `RouteOptimizer` (points + tankers + constraints to a proposal). Both take plain
value objects rather than JPA entities, which means the planning code is a pure function of its
inputs — no lazy loading, no persistence context, unit-testable without a database. Swapping in
OR-Tools or a real routing service is a bean change that touches no operations code.

**Where correctness actually lives.** Not in application memory. One published version per route,
one tanker per date and shift, one collection per farmer per stop, and capacity accumulation are
all enforced by the database — partial unique indexes, unique constraints and a row lock. The
service layer checks the same rules first, but only to produce a good error message; the database
is what makes them true under concurrency and across multiple application instances.

## 3. Domain model explanation

Twelve tables. The ones worth explaining are the relationships, not the columns.

**`CollectionPoint` 1:N `Farmer`.** A collection point is a place with coordinates. A farmer is a
person with a phone number, a payment identity and a per-shift expected volume. Route stops
reference the *place*; milk collections reference the *person*. That is what makes "some collection
points serve two farmers" ordinary data instead of a special case, and it lets service time at a
stop grow with the number of farmers there — which it does, in both the optimizer and the ETA.

**`Route` 1:N `RouteVersion` 1:N `RouteStop`.** The route is identity ("the western loop"); the
version holds the ordered stop list. A published version is immutable.

**`CollectionRun` → `RouteVersion`, never → `Route`.** This is the single most important arrow in
the model. It is what makes a run from three months ago reproducible: it resolves to the exact stop
list that was driven, even though the route has been revised twice since.

**`RunStop` → `RouteStop`,** with the plan's stop plus what actually happened (`actualArrivalTime`,
`actualDepartureTime`, status).

**`MilkCollection` → (`RunStop`, `Farmer`)** with a unique constraint on the pair. One stop, many
collections, at most one per farmer.

**`TankerLocation` → (`Tanker`, `CollectionRun` nullable).** Append-only history. The run is nullable
because a tanker reports its position whether or not it is working a run.

One detail I would volunteer: `RouteStop.plannedArrivalTime` is a `LocalTime` and
`RunStop.plannedArrivalTime` is an `Instant`. A route stop is a template — "around 05:40", true
every day. A run stop is a real event on a real date. Using the same type for both would have
blurred a genuine distinction.

## 4. Why a modular monolith?

Because the brief describes one business capability with one consistency boundary, and splitting it
would cost the thing that makes it correct.

Recording milk touches the run, the stop and the collection, and must be atomic against a capacity
limit. In a monolith that is one `@Transactional` method with a row lock — about fifteen lines. Split
across services it becomes a distributed transaction or a saga with compensations, and "reject this
collection because the tanker is full" turns into an eventual-consistency problem where the honest
answer to the driver is "we will tell you shortly whether that milk counted".

The scale argues the same way: under one write per second at peak, and a dataset of thousands of
rows a day. Microservices solve independent scaling and independent deployment for separate teams.
There is one capability and one team here, so the costs would be paid and the benefits would not
arrive.

What I did take from the modular approach is the internal boundaries: feature packages, replaceable
seams at `RouteOptimizer` and `TravelTimeProvider`, and no shared mutable state — the application is
stateless, so a second instance is a configuration change. If planning ever genuinely needed to
scale apart from operations, the package boundary is where the cut would go.

## 5. Why PostgreSQL?

Two reasons, one generic and one specific.

The generic one: the data is unambiguously relational — farmers belong to collection points, stops
belong to versions, collections belong to stops and farmers — and every query is a join or an
aggregate over those relationships.

The specific one, which matters more: **the correctness of this system is expressed in constraints
that Postgres enforces and most alternatives cannot.**

- *One published version per route*: a **partial unique index**,
  `unique (route_id) where status = 'PUBLISHED'`. Not a plain unique constraint — archived versions
  must be allowed to pile up.
- *One tanker per date and shift, unless cancelled*: `unique (tanker_id, run_date, shift) where
  status <> 'CANCELLED'`. A cancelled run releases its slot, which a plain constraint could not
  express.
- *Capacity under concurrency*: `SELECT … FOR UPDATE` on the run row.
- Plus check constraints on statuses, coordinate ranges and non-negative quantities, and `numeric`
  for litres so a comparison exactly at the capacity boundary behaves.

This is also why the integration tests use Testcontainers rather than H2: partial indexes and
`FOR UPDATE` do not exist there, so testing on H2 would test something other than what ships.

## 6. Why route versioning?

Because the brief hands you the consequence of *not* having it: routes drawn in 2019 that nobody
has touched. Two problems are tangled there, and versioning cuts them apart.

*Fear of change.* If editing the route destroys the old one, nobody edits it. With versions,
revising is additive: open a draft, change it, publish. The previous plan is archived, not lost.

*Reproducibility.* A run must remain explainable after the plan has moved on. When a load is
rejected in March and someone asks in June why the tanker took four hours, the answer has to be the
stop list it actually drove. Because `CollectionRun` points at a `RouteVersion`, that answer is
still there.

**How it is enforced.** Stops can only be written while a version is `DRAFT`; adding one to a
published version is `ROUTE_VERSION_IMMUTABLE`. Publishing archives the previous published version,
and the partial unique index guarantees at most one plan of record. Runs may only be created against
a `PUBLISHED` version — `ROUTE_VERSION_NOT_PUBLISHED` otherwise — while existing runs keep
referencing versions that have since been archived.

A practical touch: creating a draft can copy an earlier version's stops. Without that, revising an
eight-stop route means retyping eight stops, and people work around systems that make the right
thing tedious.

## 7. Why are Farmer and CollectionPoint separate?

The brief forces it: *"some collection points serve two farmers"*. If a stop pointed at a farmer,
you would have to either put CP-001 on the route twice — sending the tanker to one place two times,
corrupting stop counts, service time and ETA — or attach the milk to one farmer and lose the other,
which is a payment dispute.

They are also different *kinds* of thing. A collection point is a place: coordinates, a village, a
status. A farmer is a person: phone number, payment identity, per-shift expected volume. They have
different lifecycles — a farmer can move to a different point, a point can be closed while its
farmers move elsewhere.

So the model is `RouteStop → CollectionPoint` (a place on a route) and
`MilkCollection → (RunStop, Farmer)` (a transaction with a person). Two farmers at CP-001 mean one
`RunStop` and two `MilkCollection` rows.

The consequence I would point out as evidence the split is load-bearing: **service time at a stop is
`3 minutes + 2 minutes per farmer`**. Two farmers at one point genuinely take longer, that time
counts against the holding limit, and it delays every farmer further down the route. The optimizer,
the feasibility check and the ETA all use farmer counts per point. If farmers and points were
conflated, none of that arithmetic would be expressible.

## 8. How does tanker capacity validation work?

`MilkCollectionService.record` runs in one transaction, in this order:

1. **Take a `PESSIMISTIC_WRITE` lock on the `collection_run` row** — first, before reading anything
   the decision depends on.
2. Check the run is on the road and the tanker is standing at this stop.
3. Check the farmer belongs to this stop's collection point.
4. Check the farmer has no collection at this stop already.
5. **Sum the litres already recorded for the run** and compare `already + offered` against the
   tanker's capacity. Over capacity is `409 TANKER_CAPACITY_EXCEEDED` with the numbers in the
   message: *"Collecting 100.00 L would exceed tanker TNK-01: 4950.00 L of 5000.00 L already on
   board, 50.00 L remaining"*.
6. Check the holding time.
7. Persist.

Three things I would call out.

**The total is a `SUM` over the collection rows, not a denormalised counter.** One query per
collection, no drift, and no second number that can disagree with the rows it summarises. Under the
lock a counter would be equally correct, but it would be a fact stored twice.

**Litres are `BigDecimal` on `numeric(10,2)`.** They are summed and then compared against a hard
limit, and binary floating point misbehaves exactly at the boundary. Filling to capacity precisely
is allowed — the bound is inclusive, and there is a test asserting 4950 + 50 = 5000 succeeds while
4950 + 100 fails.

**Capacity comes from the database, never from the request.** The client sends only a farmer id and
a quantity. There is deliberately no `collectedAt` field either, because a device that chose its own
timestamp could backdate milk past the holding-time check.

Capacity is also checked *before* the run exists: run creation sums the expected volumes for the
shift and refuses a run whose route cannot fit the assigned tanker.

## 9. How do you handle concurrent collection requests?

**Two different concurrency problems, two mechanisms — that distinction is the answer.**

*Capacity accumulation* is a read-modify-write over aggregate state: read the sum, decide, insert.
Between the read and the insert, another request can do the same. So recording milk takes a
**pessimistic write lock on the run row** (`@Lock(PESSIMISTIC_WRITE)` on a `findByIdForUpdate`
query) and only then sums. The database serialises the two transactions: the second sees the first
one's litres.

```
Remaining capacity 100 L.  A offers 80 L,  B offers 60 L, simultaneously.
A takes the lock, sums 0, accepts 80, commits.
B waits, takes the lock, sums 80, sees 80 + 60 > 100, rejects with TANKER_CAPACITY_EXCEEDED.
```

`ConcurrentCollectionIT` runs exactly that from two threads with a `CountDownLatch`, and asserts
exactly one success and that the committed total never exceeds capacity. **It is a test with
teeth**: I verified that replacing `findByIdForUpdate` with a plain `findById` makes it fail with
both collections accepted and the tanker overfilled. There is also a unit test asserting
`findByIdForUpdate` is the method called, so the lock cannot be dropped silently.

*Run state transitions* are a different problem — a lost update on one row. Two dispatchers both
loading a `PLANNED` run and both completing it would have one silently overwrite the other. That is
what optimistic locking is for, so `CollectionRun` carries a JPA `@Version` and the loser gets
`409 CONCURRENT_MODIFICATION`.

Why pessimistic for capacity and optimistic for transitions: capacity conflicts are *expected* at a
busy stop and the work is short, so blocking is cheaper than a retry loop; transition conflicts are
rare accidents where failing loudly is the right outcome. Java `synchronized` is used nowhere — it
would not survive a second instance of the service, which is exactly the case you must design for.

A pleasant consequence: because all collections for a run serialise behind that lock, the duplicate
check is also race-free. Two simultaneous posts for the same farmer both hit the service check, not
just the unique constraint.

## 10. How does milk holding-time validation work?

First, the honest part: **the brief does not specify a maximum holding time.** It says milk sitting
too long is rejected. So the limit is configuration — `milk.max-holding-duration=PT4H` — and 4 hours
is a placeholder for demonstration, not a dairy regulation and not a number from the brief.

The rule is applied at two points.

**Before execution — the important one.** Creating a run projects the whole timetable from the
planned departure and checks

```
plantArrivalTime − arrivalAtFirstStop  ≤  maxHoldingDuration
```

The measurement starts at the *first stop*, not at departure, because that is when the first litre
enters the tanker. Fail and the run is refused with `MILK_HOLDING_TIME_EXCEEDED`, with nothing
persisted. Rejecting a farmer's milk at 6 a.m. because the route was never feasible is a planning
failure, and this is where it gets caught.

**At intake — the safety net.** A run can still fall behind. So every collection re-projects the
remaining route *from where the tanker actually is* and checks

```
projectedPlantArrival − oldestMilkOnBoard  ≤  maxHoldingDuration
```

**The limit applies to the oldest milk in the tanker**, because that is what decides whether the
whole load is refused at the plant. For the first collection of a run the oldest milk is the milk
being offered, so one formula covers both cases. If it fails, that collection is refused and nothing
is persisted — which leaves the milk with the farmer, who can still do something with it, rather
than adding it to a load that is already lost.

**How it is tested.** `Clock` is a Spring bean rather than a call to `Instant.now()`, precisely so
this is testable. `MilkHoldingTimeIT` collects milk, advances the clock three hours to simulate a
stuck tanker, and asserts the next collection is refused with the projected age (`PT4H12M17S`)
against the limit — then asserts nothing was written. There is a paired test showing that with an
*empty* tanker at the same late hour the milk is accepted, because the oldest milk is then the new
milk.

One design note: the optimizer's route-seeding rule falls out of this. Since holding time runs from
the first collection, a route should collect the remote milk first and finish next to the plant —
so the greedy heuristic seeds each route with the point *farthest* from the plant.

## 11. How does ETA work?

`GET /api/v1/farmers/{id}/next-collection`, and the design principle is that it is computed from the
run's **actual progress**, not from its original plan.

1. Find today's run whose route version covers the farmer's collection point, in `PLANNED`,
   `STARTED` or `IN_PROGRESS`, earliest planned start first. None → `NO_RUN_SCHEDULED` with a
   message, not an error.
2. Find the farmer's stop on that run. If it is `COMPLETED`, `SKIPPED`, or currently being worked,
   answer directly — `COLLECTED` (with the litres recorded), `SKIPPED`, or
   `AT_YOUR_COLLECTION_POINT`.
3. Otherwise **locate the tanker**:
   - run still `PLANNED` → it is at the plant, and cannot leave before its planned start;
   - on the road, no stop reached → its last reported GPS position;
   - standing at a stop → that stop's coordinates, plus the handling time still owed to farmers who
     have not been recorded there yet;
   - between stops → its last reported position.
4. Project forward with `ScheduleProjector` over the stops that are still outstanding, and read off
   the arrival at the farmer's point and how many stops precede it.

Two details worth volunteering.

**Progress is taken from the last stop with a *recorded arrival*, not the highest-numbered
non-pending stop.** A stop can be skipped out of order — skipped while the tanker is two stops back
— and a skipped stop has no arrival time. Using "not pending" would place the tanker further along
than it is and quote an ETA that is too early. There is a test for exactly that.

**Farmers still queueing at the current stop are counted in.** If the tanker is at a two-farmer point
and only one has been recorded, two minutes of handling is added before it can move on. That is the
shared-collection-point insight showing up in the farmer-facing answer.

**On honesty.** Every response carries an `estimateBasis` string: *"Estimated from the tanker's last
reported position over its remaining stops, at the configured average speed. No live traffic or
external routing data is used."* It is a projection from geometry, not a prediction, and the API says
so. The tests assert exact instants derived from the documented model, so the arithmetic itself is
under test rather than just the shape of the response.

## 12. Why not a full VRP optimizer?

Three reasons.

**Scope.** A capacitated VRP with time windows is NP-hard. In 48 hours I could write something that
*looked* like a solver and would be worse than the heuristic — and, more importantly, that I could
not defend line by line in this conversation. Everything in this repository is code I can explain.

**The constraint that binds here is not tour length.** It is the holding time, and that is
structural rather than combinatorial: collect the remote milk first and finish next to the plant.
The greedy heuristic captures exactly that by seeding each route with the point farthest from the
plant, then nearest-neighbour under both hard constraints. It gets the important thing right.

**The seam is the deliverable.** `RouteOptimizer` is a one-method interface taking plain value
objects — collection point demands, tanker capacities, planning constraints — and returning a
proposal. It has no dependency on JPA, on transactions or on operations code. Introducing OR-Tools
or Timefold is a bean swap.

I would also volunteer the heuristic's honest weakness, which shows up in the README's sample
output: it packs eight collection points onto one tanker at 16.8% capacity with 3h56m of holding
time against a 4h limit. That is feasible by the stated rule and uses one vehicle instead of two,
but four minutes of slack is not what a dispatcher wants. A greedy heuristic cannot express
"minimise vehicles *and* keep a safety margin" — that is precisely an objective function, and
precisely what a real solver is for. Today the configurable limit is the workaround: set `PT3H30M`
and the optimizer splits the work.

## 13. How would you scale to 1,400 farmers and 22 tankers?

The honest answer is that the stated load is already handled, and I would rather be concrete than
reflexively distributed.

**Writes.** 1,400 farmers × 2 shifts ≈ 2,800 collections a day, spread over two roughly three-hour
windows: under one write per second at peak. Position pings from 22 tankers every 30 seconds is
under one per second. A single Postgres instance is orders of magnitude away from noticing.

**Reads.** The busiest path is farmers checking their ETA — a handful of indexed queries each, and
the answer only changes when a position ping or a stop event arrives, so it is trivially cacheable
per collection point if it ever mattered.

**Data volume.** Everything except `tanker_location` grows by thousands of rows a day. Position
history at 22 tankers every 30 seconds is roughly two million rows a month — the one table with a
real growth story, handled by monthly partitioning and a retention policy.

**What is already in place for volume.** Indexes chosen from actual query patterns rather than
sprinkled: `farmer(collection_point_id)` and `farmer(phone)` for the call-centre lookup,
`route_stop(route_version_id)`, `collection_run(run_date, shift)` for the dispatch view, and
descending composite indexes `tanker_location(tanker_id, recorded_at desc)` so "latest position" is
a one-row index scan regardless of history size. All list endpoints are paged with a stable sort.
Response DTOs that embed associations use entity graphs or fetch joins, so a run detail is three
queries whatever its size, and farmer demand for planning is one grouped aggregate rather than a
loop over collection points.

**What I would change first, in order:** a second application instance behind a load balancer — the
application is stateless and correctness lives in database constraints and row locks rather than
application memory, which is exactly why `synchronized` appears nowhere; then a read replica for
reporting; then partitioning `tanker_location`.

At 22 tankers, Kafka and Redis would be complexity charged against no benefit. They would start to
earn their place at hundreds of tankers with real-time fan-out — see questions 16 and 17.

## 14. How would you integrate real GPS?

Mostly already done: `POST /api/v1/tankers/{id}/location` is the ingest point, and a telematics box
or a driver's phone posts HTTP anyway. Positions are append-only, and each ping is attached
automatically to whichever run the tanker is working, so the device does not need to know run ids.

What real hardware would add:

- **Device identity and authentication** — a per-device credential mapped to a tanker, so positions
  cannot be spoofed. Today the endpoint is unauthenticated.
- **Device timestamps with buffering.** Currently the server timestamps the ping on arrival, which I
  chose for consistency with milk collection, where a client-chosen time could backdate milk past
  the holding check. Real devices lose signal and replay later, so a position needs the device's own
  timestamp plus out-of-order and duplicate handling — which is why `recordedAt` is indexed
  descending rather than relying on insertion order.
- **Batch ingest**, one request carrying a few minutes of positions, instead of a request per fix.
- **Sanity filtering** — implausible jumps, stale fixes, low-accuracy readings — before a position is
  allowed to influence an ETA.
- **Retention and partitioning**, as above.

Nothing here changes the domain model. `TankerLocation` already carries what it needs, and
`EtaService` already prefers the last reported position and degrades gracefully to the last stop
with a recorded arrival when there is no fix.

## 15. How would you add real traffic-aware routing?

The seam exists: `TravelTimeProvider` is a single method, `Duration estimateTravelTime(Coordinates,
Coordinates)`, and `SimpleTravelTimeProvider` is the documented geometric implementation. Everything
that reasons about time — the optimizer, the feasibility check, the ETA — goes through it.

I would do it in two steps.

**Step one, a distance matrix.** The network is ~60 collection points and one plant, so about 3,700
pairs — a bounded, precomputable set. A nightly job against OSRM or a commercial matrix API stores
real road durations, and the provider becomes a table lookup: no latency in the request path, no
external dependency at collection time, and a big accuracy gain over straight-line × 1.3. This is
the change with the best ratio of benefit to risk, and it is the one I would actually ship.

**Step two, time-of-day and live traffic.** Store durations per pair *per time bucket*, since a
5 a.m. leg and a 6 p.m. leg differ. For live conditions, a decorating provider consults a traffic
API for the current leg only and falls back to the matrix, with a short timeout and a cache — an
ETA must never block on somebody else's API.

Two things that must not be lost in the process. Planning has to stay **deterministic and offline**
— a feasibility check that gives different answers on each call cannot be tested or trusted, so
route approval should use the matrix, not live traffic. And the honesty must be updated with the
capability: `estimateBasis` currently states no traffic data is used, and that string has to change
in the same commit that makes it untrue.

## 16. How would you introduce Redis?

I would not, at this scale — and being able to say why is more useful than adding it. There is no
cache-shaped bottleneck: the ETA is a few indexed queries, and the write path must not read from a
cache at all, since capacity and holding-time decisions have to be correct rather than fast.

The first genuine case would be **caching the ETA answer per collection point.** It is the highest-
volume read, identical for every farmer at the same point, and only changes when a position ping or
a stop event arrives — so the natural design is not a TTL but explicit invalidation on those two
events. That earns its place at, say, thousands of farmers refreshing an app.

The second would be **the latest tanker position as hot state**, if pings became frequent enough
that writing every one to Postgres was wasteful: Redis holds the current position for reads, while
the durable history still goes to Postgres, possibly batched.

What I would refuse to use Redis for is anything the correctness of the system rests on — in
particular distributed locking for tanker capacity. The database row lock is already exactly the
right tool, it is transactional with the insert it protects, and replacing it with a Redis lock
would introduce lock expiry and split-brain into the one place the system must not be wrong.

## 17. How would you introduce Kafka if the system grows?

The trigger would be **fan-out**, not throughput: several independent consumers wanting the same
operational events without the collection path knowing about any of them. Farmer SMS notifications,
a live dispatcher map, an analytics warehouse, payment reconciliation with the plant weighbridge.
Today the system logs those events; nothing subscribes to them.

The first step is not Kafka, though — it is a **transactional outbox**. Domain events
(`MilkCollected`, `RunStarted`, `StopArrived`, `RunCompleted`) get written to an `outbox` table in
the *same transaction* as the business change, and a publisher drains it. That gets exactly the
property that matters: a notification can never be sent for a collection that rolled back, and a
committed collection can never fail to notify. With the outbox in place the transport is an
implementation detail — a poller today, Kafka when there are enough consumers to justify a broker.

If it did become Kafka: topics keyed by run id so a run's events stay ordered on one partition;
consumers idempotent, because at-least-once delivery is the only honest guarantee; and — the line I
would hold — **the write path stays synchronous.** Capacity and holding-time validation must remain a
transactional decision at the moment milk is offered. "You may load that milk, we will tell you
later whether it counted" is not an acceptable answer to a driver with a hose in his hand. Kafka
would carry consequences of decisions, never make them.

## 18. How would you deploy it?

The artefact is a single jar and a multi-stage Docker image that runs as a non-root user, so the
deployment story is deliberately unremarkable.

- **Config** is entirely environment variables with sane defaults and no committed credentials;
  `DB_PASSWORD` comes from the platform's secret store.
- **Database**: managed Postgres (RDS, Cloud SQL) with automated backups and PITR. Migrations run at
  startup via Flyway with `ddl-auto=validate`, so the application refuses to boot against a drifted
  schema. For a multi-instance rollout, migrations must be backwards-compatible for one version —
  add columns before writing them, drop them a release later — or run as an explicit pre-deploy step
  so two versions never race Flyway.
- **Runtime**: two or more instances behind a load balancer. The application is stateless, and
  correctness lives in the database, so horizontal scaling needs no code change.
- **Health**: `/actuator/health` with liveness and readiness groups enabled, wired to the platform's
  probes; readiness fails while the database is unreachable, so an instance is removed from rotation
  rather than serving errors.
- **Pipeline**: `mvn verify` on every commit — the integration tests run against real Postgres via
  Testcontainers, which works unchanged in CI — then build the image, then deploy.
- **Before I would call it production-ready**: authentication (question 14's device credentials and
  the role model from the README), structured JSON logs with correlation ids, and metrics on the
  operational events already being logged. Those are gaps I would name rather than paper over.

## 19. What would you change with another week?

In the order I would actually do it.

1. **Authentication and role-based authorisation** — planner, driver, farmer — with a farmer scoped
   to their own data. `GET /farmers/{id}/next-collection` is currently unauthenticated and would
   need the caller's identity bound to the farmer. Biggest gap to production, so it goes first.
2. **A correction and void flow for collections**, with an audit trail. Field data entry is
   sometimes wrong and today that means editing the database by hand. This is the assumption I am
   least comfortable with.
3. **A real distance matrix behind `TravelTimeProvider`** (question 15, step one). Sharpens
   feasibility and ETA more than any change to the optimizer.
4. **Notifications through a transactional outbox** — "your tanker is 20 minutes away" — which is
   the feature the farmer actually wants and the natural first event consumer.
5. **Route version diffing**, showing a planner exactly what changed between two versions. The
   immutable-version model already makes it possible and it is what makes the planning UI usable.
6. **Retention and partitioning for `tanker_location`**, before it needs to be urgent.
7. **Idempotency keys** on the collection and position endpoints, so a device on a flaky connection
   can retry safely instead of relying on the duplicate check.

Notably *not* on the list: a full VRP solver. It is the most interesting problem here and the least
valuable next step — the plans the heuristic produces are feasible, and better data (step 3) would
improve outcomes more than a better search over worse estimates.

## 20. What are the biggest limitations of the MVP?

Stated plainly, worst first.

1. **No authentication or authorisation at all.** Any caller can read any farmer's data or start any
   run. I would rather say that than ship a token check that looks like security.
2. **Travel times are geometric, not real.** Straight-line distance × 1.3 ÷ 30 km/h. Every estimate
   in the system inherits that error — including the feasibility check that decides whether a run
   may exist. It is deterministic and documented, and every ETA response says so, but it is not
   accurate. A hilly or badly connected 12 km could easily take twice the estimate.
3. **The 4-hour holding limit is my assumption, not a requirement.** The brief gives no threshold.
   Everything about spoilage prevention here is calibrated to a placeholder, and the real number
   would change which plans are feasible.
4. **No correction path for a recorded collection.** One collection per farmer per stop, and a wrong
   quantity needs a database fix.
5. **The optimizer has no safety margin.** As above: it will fill a tanker to 3h56m of holding
   against a 4h limit because that is feasible by the stated rule. Operationally you would want
   slack, and today the only control is lowering the configured limit.
6. **Server-assigned timestamps mean no offline operation.** Rural collection is exactly where
   connectivity fails, and a device that buffers a morning's collections and syncs later would
   record them all at sync time — which would corrupt the holding-time reasoning. Real offline
   support needs device timestamps, idempotency keys and a trust model for both.
7. **Runs are single-tanker and single-plant.** No transfer between tankers, no second plant, no
   partial unload — all of which real dairies do.
8. **No notifications.** The farmer must ask; the system never tells them.
9. **The planning proposal is not connected to route creation.** `POST /planning/optimize` returns a
   plan and a human retypes it as route versions. Turning a proposal into draft versions in one call
   is an obvious and small next step.
10. **Position history has no retention policy**, so it grows without bound.

What I am confident *is* solid: the domain model, the versioning guarantee, the transactional and
concurrency handling around capacity, and the test coverage of the business rules — 149 tests, with
the ones that matter asserting exact arithmetic against real PostgreSQL rather than just checking
that responses have the right shape.
