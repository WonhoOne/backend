# Reservation domain and snapshot persistence (B4)

Backend-local implementation notes for FR-04/05/06/09/10/15, NFR-02/06 and
BR-03/04/05/07/13/15/18/20/21/22/23/26/30/31. Approved shared source:
[Baseline v0.2](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/baseline/BASELINE-v0.2.md),
[business rules](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/requirements/business-rules.md)
and [REST contract](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/api/api-spec-draft.md).
Backend base: `576487c734282ecf7614a6ec2dfc1a409003c220`.
Shared Contract changed: **NO**. Public wire definitions remain in the SSOT.

## Domain rules

`ReservationPartyPolicy` is the entry point for participant validation. General
themes accept 1..10; Honeymoon accepts 2/4/6/8/10. `honeymoonCoupleCount` validates
then derives participantCount / 2. No couple/team identity or stored count exists.
The legacy pure `Reservation` delegates its base range to this policy, and pure
`TourSchedule` delegates Theme-specific validation before changing in-memory state.

`TourConfiguration` is a pure immutable value: Style, Hotel, Transport, Meal and
an unordered unique Extra set. The collection factory rejects duplicates and null
entries before creating a defensive immutable set. B5 must pass the original
request List to `create`; converting it to a Set first would hide invalid duplicates.
The Set constructor also copies its input; callers cannot mutate stored selection.

Canonical options are `HotelOption` (HOTEL_3_STAR/HOTEL_4_STAR/HOTEL_5_STAR),
`MealOption` (LUNCH_BOX/LOCAL_RESTAURANT/PREMIUM_RESTAURANT), `TransportOption`
(PRIVATE_LUXURY_CAR_2/PREMIUM_VAN_10) and `ExtraOption` (CHAMPAGNE/COFFEE).
Transport capacity lives on the enum as 2 or 10 participants.

`ReservationConfigurationPolicy.validate` checks party size, delegates permitted
Theme/Style combinations to `TourStylePolicy`, then compares vehicle capacity with
participantCount. Hotel, Meal and Extra are editable canonical selections. Premium
Champagne may be removed; Style defaults are never mandatory final combinations.
There is no multi-vehicle allocation, option surcharge or Inventory dependency.

## Price and Loyalty

`ReservationPriceCalculator.calculate(unitPrice, participantCount, loyaltyEligible)`
contains the calculation. Money uses Java long / SQL BIGINT, exclusively whole KRW:

```text
subtotal = unitPrice * participantCount       (Math.multiplyExact)
discount = null                              (new Customer)
discount = LOYALTY, ratePercent 5,
           floor(subtotal * 5 / 100)          (eligible Customer)
total = subtotal - discount.amount           (zero subtraction if discount is null)
currency = KRW
```

Honeymoon pricing also uses participants. Options do not enter the calculator.
The positive BigInteger intermediate in `loyaltyAmount` explicitly floors integer
KRW division without overflowing when subtotal fits BIGINT. 101 KRW becomes a
5 KRW discount and 96 KRW total. An eligible subtotal under 20 still records Loyalty
with a zero amount. Multiplication overflow raises ArithmeticException; B5 must
review public error mapping without inventing a new shared price maximum or code.

`DiscountSnapshot` validates the sole supported type/rate and nonnegative amount.
`ReservationPriceSnapshot` validates KRW, money ranges, the exact rounded Loyalty
amount and total consistency. `ReservationJpaEntity.capture` additionally verifies
subtotal matches unitPrice × this Reservation's participants.

`LoyaltyEligibilityService.isEligible` reuses `BusinessDateProvider` in a read-only
transaction. `ReservationJpaRepository.countCompletedTrips` selects the Customer's
Reservations whose **current persisted Schedule confirmed is true** and whose
**historical scheduleEndDateSnapshot is strictly before today's business date**.
Same-day endings, future endings and unconfirmed past trips do not qualify. One
completed trip suffices; other Customers' trips never qualify the current owner.
B5 must call eligibility **before saving the new Reservation** inside its create
transaction so the new row cannot qualify itself.

## What the snapshots preserve

`ReservationJpaEntity.capture(customer, schedule, participantCount, configuration, price)`
copies Product id/Theme/name and Schedule dates immediately, plus final configuration
and calculated price. It offers no public snapshot mutation methods. Scalar mappings
are updatable=false and returned domain values defensively copy collections.

Product name and Style prices remain editable after a Schedule exists. Their current
values cannot replace the historical trip's name or price. Theme is also captured to
make historical representation explicit. Dates are captured as a Backend-local choice
for stable History periods even if a later internal process changes current dates.
Product snapshot identity is a scalar, with no additional Product FK.

`confirmed` must remain dynamic: a Schedule may first confirm after a Reservation was
saved. Loyalty and History consult the current Schedule row. `reservable` is also
dynamic and derived from the latest business date; B5 checks it at creation, without
storing it on a Reservation. Customer profile/contact is not snapshotted; later SMS
must use currently stored contact. No ReservationStatus or TravelHistory table exists.

## Physical schema and mapping

V1/V2 remain immutable. `V3__create_reservation_snapshots.sql` adds:

| Table | Stored meaning |
| --- | --- |
| tour_reservation | identity, Customer/Schedule FKs, participantCount, Product identity/Theme/name, Schedule dates, configuration scalars, unitPrice/subtotal/nullable discount/total/currency |
| tour_reservation_extra_option | reservation_id + extra_option composite primary key; owner FK with ON DELETE CASCADE |

Checks enforce 1..10 participants, positive Product snapshot identity/unitPrice/subtotal,
nonnegative total, date order, KRW and canonical enum literals. Discount is either all
three columns null with total=subtotal, or LOYALTY/5/nonnegative amount with consistent
total. Explicit IS NOT NULL terms prevent SQL CHECK's unknown/null result from accepting
a partially populated discount. Exact discount rounding, subtotal multiplication,
Theme/Style, Honeymoon and capacity remain domain/factory invariants; direct SQL writers
must use equivalent validation. No new public constraints are introduced.

Indexes are `(customer_id, schedule_end_date_snapshot)` for Customer/History/Loyalty
lookups and `(tour_schedule_id)` for Schedule lookup. The composite Customer index's
leading column covers Customer-only queries without a redundant index.

JPA uses separate `ReservationConfigurationEmbeddable` and
`ReservationPriceSnapshotEmbeddable`. Extra is an ElementCollection of values with
explicit table naming. Enums use STRING plus explicit VARCHAR JDBC mapping.
Customer and Schedule associations are LAZY, without cascade save/delete. Existing
open-in-view=false stays in effect. Read configuration inside a service transaction
and project it to a DTO; never serialize the JPA entity at the controller boundary.
The factory owns snapshot capture and invariant checks, while B5 owns authentication,
CUSTOMER role authorization, current price lookup and the complete create transaction.

## BR-31 alignment

Employee Product PUT validates a positive ID, loads the Product and validates the
replacement request. If Theme differs, `existsByTourProductId` checks for any linked
Schedule before details or prices are mutated. A linked row returns
409 TOUR_PRODUCT_THEME_LOCKED with D-10 fieldErrors=[]. Future, same-day, past and
confirmed/unconfirmed rows all lock Theme. Same-Theme name/description/price replacement
continues through the existing transaction. A Schedule on another Product has no effect.
Public Schedule creation does not exist; future internal provisioning must coordinate
with Product writes if concurrent Schedule insertion is introduced.

## B5 and B6 integration boundaries

B5 create flow should resolve the authenticated CUSTOMER and Schedule, validate current
reservability, validate party/configuration, read the selected Style's current price
through the existing StylePrice repository, query Loyalty before save, calculate price,
capture snapshots, save, and update recruitment/confirmation within its chosen transaction
and locking strategy. This document describes the integration order, not an implemented
Reservation orchestration or concurrency guarantee.

Reservation rows now exist, but public recruitment aggregation remains B5 work.
`TourScheduleQueryService` still supplies currentCount=0. B5 connects persisted aggregates,
ownership detail, Schedule locking, confirmation transition and SMS coordination.
B6 can project History from Reservation id, captured Product identity/Theme/name, captured
dates, configuration.style and price.total/currency, together with current confirmation.
No speculative aggregate, locking, ownership or History query methods are added in B4.

The original pure `TourScheduleReservationService` remains a legacy in-memory foundation.
Its synchronous SMS example does not implement final recipient deduplication, retry or
failure isolation. B5/B8 connect the final persistence-backed workflow; B4 preserves it.

## Presentation walkthrough

1. Configuration: Theme → ReservationPartyPolicy → TourStylePolicy → transport.capacity
   → final TourConfiguration. Explain why a default Hotel/Meal may be changed.
2. Pricing: selected current Style price → participantCount → subtotal → Loyalty query
   → explicit floor of 5% → total → ReservationPriceSnapshot.
3. Persistence: Customer/Schedule references + captured Product identity/name/Theme +
   captured period + immutable configuration + calculated price → capture → repository.
4. Show a Product edit and Reservation reload in ReservationPersistenceTests. Current
   name/price changes; the historical Reservation retains its original values.
5. Dynamic state: show LoyaltyEligibilityTests updating Schedule confirmation after
   capture. Current confirmed affects eligibility; historical endDate stays stable.
6. Theme lock: Employee PUT → validation → linked Schedule existence → 409 or unchanged
   replacement flow. ThemeLockIntegrationTests exercises the real SecurityFilterChain.

## Verification limits and scope

Tests use the same V1/V2/V3 Flyway migrations and Hibernate validate on fresh H2 databases
in MySQL mode. Domain tests are Spring-free; persistence/Loyalty tests roll back their
transactions; BR-31 endpoint tests verify stored state after HTTP conflict.
No demo Reservation/Product/Schedule seeds or Employee provisioning changes are added.

Actual MySQL 8 remains the production truth. H2 does not establish MySQL CHECK semantics,
enum string collation, FK/index behavior or transaction/concurrency behavior. Validate
these during later integration/hardening; no Docker/Testcontainers dependency is added.
Reservation REST/DTOs, concurrency/locking, actual recruitment aggregates, confirmation
transitions, History REST, Inventory REST, SMS delivery/outbox, update/cancel/payment and
idempotency remain outside B4. No Shared Contract blocker was found.

Local verification: the original main baseline passed 152 tests. B4 adds 77 scenarios
across six groups, for 229 total with zero failures, errors or skipped tests. The
Maven Wrapper test and package gates execute the same tests; git diff --check is
also required before submission. CI and actual MySQL validation are separate gates.
