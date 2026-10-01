# Tour catalog and schedule API (B3)

Backend-local implementation notes for FR-02, FR-07, FR-10 and BR-03, BR-04,
BR-17, BR-19, BR-21, BR-29, BR-30. Approved shared reference:
[Baseline v0.2](https://github.com/WonhoOne/docs/blob/c38995d1c33eabc73c2378335a1536b0d9ae4b1e/baseline/BASELINE-v0.2.md)
and its [REST contract](https://github.com/WonhoOne/docs/blob/c38995d1c33eabc73c2378335a1536b0d9ae4b1e/api/api-spec-draft.md).
Shared Contract changed: **NO**.

## Product reads

PublicTourController and EmployeeTourController share TourProductQueryService.
The read-only transaction retrieves products in ID order, then prices for all
product IDs in one query. Grouping prices by product avoids per-product queries.
Detail uses one product lookup and one price lookup. TourProductResponseFactory
projects explicit DTOs; persistence identities and lazy relationships never
become public fields.

TourStylePolicy.allowedStyles is the deterministic Theme → allowed styles
boundary. Response availableStyles is derived here; there is no stored column.
The factory emits prices in the same CLASSIC, GRAND, PREMIUM order, omitting
CLASSIC for restricted themes. Currency is projected as KRW, without a new
database column. An incomplete or disallowed persisted price set raises a safe
INTERNAL_ERROR instead of publishing an invalid representation. B3 creates no
production seed; any external provisioning must provide complete prices.

## Employee writes

EmployeeTourController owns request validation and HTTP 201/200 semantics.
SecurityConfig's existing Employee rule governs collection reads and writes.
TourProductCommandService owns the transaction. Bean Validation checks required
fields, positive whole-long prices and existing storage bounds (name 255,
description 2000). These bounds prevent truncation/storage errors and introduce
no new shared business rule. Strict Jackson rejects unknown properties, unknown
enum literals, fractional prices, and numeric strings in monetary fields.

TourProductWriteValidator checks the complete style set, duplicates, disallowed
styles, missing prices and KRW currency. Semantic failures collect existing D-10
field codes with request paths. Bean Validation failures preserve nested paths
such as stylePrices[0].amount. Null entries fail required validation before the
semantic validator runs.

POST saves product and the complete price set together. PUT loads the product,
validates the complete replacement, updates details, deletes old prices, flushes
deletes, and inserts the new prices. The explicit flush prevents reuse of the
unique (product, style) key before old rows are deleted. Style-price row IDs are
not public identity. Any later persistence failure rolls the whole transaction
back, including flushed deletes and earlier inserts. Integration tests inject a
failure on a later price insert to verify both POST and PUT rollback.

## Schedule reads and business date

B4 enforces approved BR-31 before Product PUT mutation: a different Theme with any
linked Schedule returns 409 TOUR_PRODUCT_THEME_LOCKED. Same-Theme replacement of
name/description/prices remains allowed. See
[reservation-domain-persistence.md](reservation-domain-persistence.md) for the
approved `cad8daed210cfb60078f24cabe14c2f383f3ef65` reference and endpoint scenarios.

TourScheduleController delegates to TourScheduleQueryService's read-only
transaction. Schedule repository methods explicitly order startDate then ID and
fetch the product using EntityGraph so Theme is available with open-in-view=false.
The optional tourId filter queries schedules directly: an unknown positive
product ID returns an empty array. Nonpositive, malformed, overflow and explicitly
empty filters return INVALID_QUERY_PARAMETER. Missing positive detail IDs return
the resource-specific 404. No schedule write API is added.

BusinessTimeConfig provides Clock.system for business.time-zone, default
**Asia/Seoul**, overridable through **BUSINESS_TIME_ZONE**. The zone is a
Backend-local default; the Shared Contract does not mandate a particular zone.
BusinessDateProvider.today uses LocalDate.now(clock). A collection takes one
business date for all its items, avoiding inconsistent results across midnight.
Tests replace businessClock with a fixed Clock at business date 2026-10-01 and
also check a UTC/Seoul date boundary. Travel History and Loyalty can reuse this
provider. B5 Reservation creation obtains the latest date after locking and revalidates
the same pure policy, regardless of a previously read DTO.

TourScheduleReservabilityPolicy contains the complete BR-30 rule:
startDate.isAfter(businessDate). Same-day and past departures are false.
Persisted confirmed is departure confirmation; it does not close intake for a
future departure. The policy intentionally takes only dates so confirmation
cannot accidentally become a reservation-close condition.

## Recruitment and the B3 boundary

ScheduleRecruitmentProjection accepts Theme, an aggregate count already in its
recruitment unit, and persisted confirmed. Honeymoon uses COUPLE_TEAM and required
count 2; the other themes use PARTICIPANT and required count 3. The projection
preserves persisted confirmation rather than recomputing it from the count.

B5 connects real persisted Reservation participant sums. TourScheduleQueryService
uses one grouped aggregate query for collection IDs and one SUM for detail.
TourScheduleRecruitmentPolicy converts participants into Honeymoon couple/teams,
and ScheduleRecruitmentProjection delegates unit/required count to that same pure
policy. A confirmed fixture may deliberately have count zero to verify that
projection preserves persisted state. B5 create transactions own first confirmation;
read APIs do not reconcile or mutate it. See
[reservation-api-concurrency.md](reservation-api-concurrency.md).

## Errors and diagnostic logging

ApiExceptionHandler reuses D-10: 400 malformed request/invalid parameter, resource
404, and 422 validation with existing field code vocabulary. Unexpected failures
return a fixed INTERNAL_ERROR body. Internal diagnostics log exception type,
HTTP method and matched route pattern. A route pattern is used instead of raw
URI/query values, preventing user-supplied IDs or profile-like values entering
the log. Bodies, credentials, tokens, exception messages and stack traces are
excluded from this handler's log.

## Presentation walkthrough

1. Open PublicTourController → TourProductQueryService → TourStylePolicy →
   repositories → TourProductResponseFactory. Explain why styles come from Theme
   and prices come from product rows.
2. Open EmployeeTourController → TourProductWriteValidator →
   TourProductCommandService. Follow validation before mutation and explain the
   flush before reusing unique keys and transaction rollback.
3. Open TourScheduleController → TourScheduleQueryService → BusinessDateProvider
   → TourScheduleReservabilityPolicy → ScheduleRecruitmentProjection → DTO.
   Compare a confirmed future departure with a same-day departure.
4. Show TourApiIntegrationTests' fixed clock, public access, Employee access,
   complete price validation and persistence-failure rollback scenarios.

## Scope and verification limits

V1/V2 remain immutable. B3 added no migration; B4 adds V3 for Reservation snapshots.
There is no demo product/schedule seed.
At the original B3 gate Reservation persistence and snapshots/Loyalty were deferred.
B4 implements those foundations and BR-31; B5 implements Reservation API, locking,
recruitment and confirmation. B6 implements [History REST](travel-history.md).
Inventory API/mutation, SMS,
schedule CRUD/capacity/close/cancel and pagination remain deferred.
H2 MySQL-mode tests execute Flyway and schema validation and exercise the real
SecurityFilterChain. Real MySQL collation and concurrent signup mapping remain
integration/hardening work; H2 does not prove those engine properties.
