# Reservation API, recruitment and concurrency (B5)

Backend-local implementation for FR-04/05/06/07/08/15, NFR-02/06 and
BR-03/04/06/07/13/15/18/19/20/21/22/23/26/30/31. BR-14/27's first-confirmation
boundary is prepared; delivery belongs to B8. Approved shared reference:
[Baseline v0.2](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/baseline/BASELINE-v0.2.md),
[REST contract](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/api/api-spec-draft.md)
and [business rules](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/requirements/business-rules.md).
Backend base: `48d88ca8f793a081abe0516c32ddad2e6583f6a9`.
Shared Contract changed: **NO**. Public DTO meanings remain in the SSOT.

## POST walkthrough

`ReservationController` implements POST /api/v1/reservations with HTTP 201. Existing
SecurityConfig requires CUSTOMER; AuthenticatedUser extracts the verified JWT's
account ID. The controller delegates the ID and validated request to
ReservationCommandService and returns only the result's response. Location is
omitted, as permitted by the contract; no other Reservation endpoints are added.

Read `ReservationCommandService.create` from top to bottom:

1. Resolve the authenticated Customer account. A missing/inconsistent account fails
   with safe INTERNAL_ERROR; the request never supplies an owner.
2. Lock the target Schedule through `findByIdForReservationUpdate`.
3. Obtain the latest BusinessDateProvider.today **after acquiring the lock**.
4. Revalidate TourScheduleReservabilityPolicy. Same-day/past departure returns
   409 SCHEDULE_NOT_RESERVABLE; a confirmed future Schedule remains reservable.
5. ReservationCreateValidator reports semantic field errors, then creates the
   domain configuration from the original Extra List and calls
   ReservationConfigurationPolicy, including ReservationPartyPolicy and TourStylePolicy.
6. Read the selected Product/Style's current committed price row.
7. Query LoyaltyEligibilityService **before saving**. Current confirmed plus
   historical endDate before business date qualifies; other owners do not.
8. Calculate price with ReservationPriceCalculator. Checked BIGINT overflow is a
   safe 500 INTERNAL_ERROR and rolls back, without a new max-price rule or code.
9. ReservationJpaEntity.capture preserves B4's historical snapshot values.
10. saveAndFlush makes the new persisted row visible to the aggregate query.
11. Query SUM(participantCount) for this Schedule from persisted Reservations.
12. TourScheduleRecruitmentPolicy converts the participant sum into the Theme's unit.
13. If the threshold is reached, markConfirmed changes false to true only once.
14. Flush Schedule state so an update failure rolls back the whole transaction.
15. ReservationResponseFactory combines snapshots and current recruitment into the DTO.

Bean Validation supplies REQUIRED and OUT_OF_RANGE at top-level/nested paths.
Semantic validation supplies participantCount NOT_ALLOWED, configuration.style
NOT_ALLOWED, configuration.transportOption CAPACITY_EXCEEDED and
configuration.extraOptions DUPLICATE_VALUE. Null fields/elements fail before the
semantic boundary. Strict Jackson rejects unknown enums/properties and fractional
or string integers with 400 MALFORMED_REQUEST. Domain checks remain final invariants;
the application boundary adds readable D-10 field paths, not alternative rules.

## Why the Schedule row is locked

Without a lock, two creates could both observe confirmed=false and decide they
were the first threshold crossing, or each aggregate could miss the other's row.
The explicit JPQL lock query uses PESSIMISTIC_WRITE inside the active create write
transaction. It targets only the Schedule row; it has no Product EntityGraph join.
Product is accessed lazily inside that transaction. There is no Customer lock,
Product lock, application global mutex or POST replay/retry loop.

Same-Schedule creates serialize until commit/rollback, including validation,
save, aggregate and confirmation. Different Schedule rows use independent locks.
All B5 create writers use this boundary; external SQL writers must preserve the
same discipline and domain invariants. No reconciliation or unconfirm operation exists.

The create transaction explicitly uses READ_COMMITTED. With MySQL's common
REPEATABLE_READ default, the account lookup before lock waiting could establish an
older consistent-read snapshot; later nonlocking SUM could miss the previous
creator's commit even though the Schedule lock is serialized. READ_COMMITTED gives
the aggregate a fresh statement snapshot after the lock has been acquired. This
is a Backend-local isolation choice, not a new shared rule. It also reads committed
Style prices without exposing an Employee replacement transaction's intermediate
uncommitted delete/insert. Price writes have no new shared serialization rule.

## Recruitment and confirmation

ReservationJpaRepository.totalParticipantsForSchedule uses a coalesced participant
SUM (zero for no rows). totalParticipantsByScheduleIds performs one grouped query
for all collection Schedule IDs, with a small interface projection. Schedule list
loads Schedules and Products once, then aggregates once; it does not issue a SUM
per item. Missing groups default to zero. Detail uses one Schedule read plus one SUM.

TourScheduleRecruitmentPolicy is the common pure boundary for public Schedule reads,
Reservation POST/detail and confirmation thresholds:

| Theme | Unit | Current count | Required count |
| --- | --- | --- | --- |
| HONEYMOON_ROMANCE | COUPLE_TEAM | participantTotal / 2 | 2 |
| Other Themes | PARTICIPANT | participantTotal | 3 |

An odd Honeymoon aggregate fails as an internal invariant instead of truncating.
Counts use long and are never clamped to the per-Reservation maximum of ten.
Threshold comparison is >=, so one general five-person or Honeymoon six-person
Reservation can confirm immediately. ScheduleRecruitmentProjection delegates
unit/required count to this policy and preserves the persisted confirmed flag.

TourScheduleJpaEntity.markConfirmed is a one-way operation returning true only on
false to true. ReservationCreationResult carries response + scheduleJustConfirmed;
the boolean never enters public JSON. A later Reservation on a confirmed Schedule
returns false even while recruitment keeps growing.

B5 creates the reliable transition boundary; B8 attaches notification semantics.
B8 should attach an event/hook within this transaction and handle notification
after commit, rather than send SMS from the controller or infer transitions from
public confirmed=true. The returned result becomes available to the controller
only after a successful transaction commit. B5 publishes no event, sends no SMS,
collects no recipients and adds no outbox/provider/retry. The legacy in-memory
TourScheduleReservationService is not called by the persisted flow.

## GET ownership and response semantics

GET /api/v1/reservations/{reservationId} follows ReservationController →
ReservationQueryService → findOwnedReservation → participant aggregate →
ScheduleRecruitmentProjection → ReservationResponseFactory. The read-only service
validates a positive ID before querying. Absent or foreign-owned rows return the
same 404 RESERVATION_NOT_FOUND without a separate existence check. Nonpositive,
malformed or overflowing path IDs return 400 INVALID_QUERY_PARAMETER.

| Source | Response values |
| --- | --- |
| Reservation-time snapshots | Product id/Theme/name, Schedule period, configuration, price/discount |
| Current Schedule/Reservation state | Schedule id and recruitment unit/currentCount/requiredCount/confirmed |

Later Product name/pricing edits and internal Schedule period changes do not
rewrite historical values. Later Reservations do change an earlier Reservation's
detail recruitment. BR-31 stabilizes the current Schedule → Product Theme used for
recruitment; no Schedule Theme snapshot or new schema is needed.

Explicit nested DTOs contain no JPA entities or Customer profile. Extra options are
serialized in enum order (CHAMPAGNE, COFFEE) for stable Backend output, while shared
meaning remains an unordered unique selection. Non-Loyalty discount is explicitly
null. confirmed/reservable are not Reservation snapshots, and there is no
ReservationStatus, coupleCount field or Customer contact snapshot.

## Verification and presentation

Tests are separated into REST/snapshot/ownership, validation/rollback, recruitment,
concurrency and Spring-free recruitment policy groups. They use the real security
filter, H2 MySQL mode, unchanged V1/V2/V3 Flyway scripts and Hibernate validation.
Reservation and B6 History paths are tested against production controllers.
B6 removes the former Customer identity probe mapping; Employee probes remain.
See [travel-history.md](travel-history.md) for the snapshot-only collection read.

The 229-test B4 baseline is preserved. B5 adds 76 tests (28 API, 21 validation,
17 recruitment, 4 concurrency and 6 pure policy), for 305 total with zero failures,
errors or skipped tests. Maven Wrapper test/package and git diff --check are the
submission checks; CI and actual MySQL remain separate verification boundaries.

Concurrency tests start two workers together, expect two rows, participant sum 4,
responses with counts 2 and 4, confirmed=true and exactly one scheduleJustConfirmed.
A held-lock scenario proves a second same-Schedule create waits and sees the earlier
commit. A different-Schedule scenario shares the Product and completes while the
first Schedule stays locked. Coordination and future waits are bounded; workers
are released/shut down in finally blocks. The confirmation-update trigger failure
test proves flushed Reservation/Extra rows and confirmation roll back together.

For a presentation, show Controller → AuthenticatedUser → CommandService's ordered
method → repository lock annotation → domain validation → current price/Loyalty →
capture → saveAndFlush → SUM → recruitment conversion → markConfirmed → response.
Then show GET's owner-scoped query and the snapshot/current table above. Finally
show the same-Schedule lock test: one row lock, serialized creates, one first transition.

Actual MySQL 8 PESSIMISTIC_WRITE, waiting/isolation behavior, CHECK and FK/index
semantics remain unverified. H2 success is not proof of those engine properties;
no Docker/Testcontainers is added here. V1/V2/V3 are unchanged and there is no V4.
B6 implements History REST over snapshots. Inventory REST (B7), SMS delivery (B8), schedule CRUD/capacity/manual
close, Reservation update/cancel, payment/refund and idempotency remain deferred.
