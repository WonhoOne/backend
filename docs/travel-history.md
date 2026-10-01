# Travel History snapshot projection (B6)

Backend-local implementation for FR-09/15, BR-08/09/15/22/23/29 and NFR-02/06.
Approved source: [Shared Baseline v0.2](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/baseline/BASELINE-v0.2.md)
and its [REST contract](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/api/api-spec-draft.md).
Backend base: `a5245d319022c91896874032801cf876e0bbd470`.
Shared Contract changed: **NO**.

## Request walkthrough

GET `/api/v1/customers/me/travel-history` follows:

```text
CustomerHistoryController
  → verified Jwt → AuthenticatedUser → authenticated Customer ID
  → TravelHistoryQueryService (read-only transaction)
  → BusinessDateProvider.today (once per request)
  → ReservationJpaRepository.findTravelHistory
      WHERE customer.id = authenticated ID
        AND current tourSchedule.confirmed = true
        AND historical scheduleEndDateSnapshot < business date
      ORDER BY scheduleEndDateSnapshot DESC, reservation.id DESC
  → TravelHistoryRow scalar projection → TravelHistoryResponse → plain JSON array
```

The existing SecurityConfig rule requires CUSTOMER. Anonymous requests return
401 AUTHENTICATION_REQUIRED; EMPLOYEE requests return 403 FORBIDDEN. Invalid and
expired tokens retain B2 errors. The controller only extracts identity and delegates.
There is no account lookup, request parameter, filter, pagination or envelope.
No eligible rows returns 200 `[]`.

## Historical values and current state

| Source | Used for |
| --- | --- |
| Reservation.tourProductIdSnapshot / tourProductThemeSnapshot / tourProductNameSnapshot | Product summary |
| Reservation.scheduleStartDateSnapshot / scheduleEndDateSnapshot | Displayed period; snapshot endDate also determines completion |
| Reservation.configuration.style | Selected Style at reservation time |
| Reservation.price.total / currency | History amount and currency, including any captured Loyalty discount |
| Current TourSchedule.confirmed | Completion eligibility only |

A trip ending on the business date is excluded, as are future endings and
unconfirmed past trips. Later confirmation can make an existing historical
Reservation visible. A current Schedule date change cannot rewrite its period
or completion boundary. Product name/pricing edits cannot rewrite the summary,
selected Style or paid total. No current Product or StylePrice query is needed.
Business date uses the existing configurable Clock, default Asia/Seoul via
BUSINESS_TIME_ZONE. Tests fix it to 2026-10-01.

TravelHistoryResponse has six fields: reservationId, tourProduct, startDate,
endDate, style and price. Its nested Product summary has id/theme/name; price
has amount/currency. Personal data, participantCount, options, image, discount
details, confirmation and status are omitted. No detail route is introduced.

## Query and persistence choices

History is a view of existing immutable Reservation snapshots. Persisting a
second History table would duplicate values and require synchronization.
V1/V2/V3 remain unchanged; Flyway stays at version 3. No History entity or status
is added. This read performs no locking, writes, cache or reconciliation.

The repository selects nine scalars into an interface projection, separate from
the public DTO. It joins the current Schedule only for confirmation. It does not
materialize Reservation entities or call getConfiguration(), which could load
the Extra ElementCollection. Query work remains one scalar query for an empty
or multi-row result, without per-row Product/Extra/StylePrice queries or aggregates.
The existing customer/endDate index supports the owner/date lookup; actual MySQL
plans and performance remain integration/hardening validation.

Loyalty's countCompletedTrips and History's findTravelHistory intentionally keep
the same explicit completed predicate. B6 does not change Loyalty behavior or
introduce a generic query framework. A shared fixture test checks same-day,
unconfirmed past and confirmed past outcomes for both services.

## Tests and presentation

TravelHistoryIntegrationTests reuses B5's persisted fixture/security/Clock helpers.
Historical rows are captured through ReservationJpaEntity.capture and
ReservationPriceCalculator, representing reservations created in the past.
They are not public POSTs to now-expired schedules. JDBC changes only current
Schedule dates/confirmation to demonstrate the snapshot/current distinction.
Product edits use the real Employee PUT endpoint.

The 20 B6 tests cover empty results, ownership, strict dates, confirmation changes,
tie ordering, exact fields, private-data exclusion, historical Product/price/period/
Style, discounted total, security and Loyalty consistency. AuthIntegrationTests
now uses the production History endpoint, keeps JWT claims/statelessness/invalid
token regressions, and retains only Employee probe mappings.

Local verification preserves the 305-test baseline and adds 20 tests, for 325
total with zero failures, errors or skipped tests. Maven Wrapper test and package
both pass; package builds the executable JAR. git diff --check passes. Flyway
version 3 and the three existing migrations remain covered by persistence tests.

For the presentation, open the controller, service, repository query and DTO in
that order. Point to `tourSchedule.confirmed` beside `scheduleEndDateSnapshot`,
then show Product/date edit tests:

> “History는 현재 상품 정보를 다시 읽는 기능이 아니라 Reservation 당시 저장한
> snapshot을 조회하는 기능입니다. 단 여행 완료 여부의 confirmed는 현재 Schedule
> 상태를 사용합니다. 상품명이 나중에 바뀌어도 과거 여행 기록은 그대로입니다.”

## Remaining gates

B7 Inventory, B8 actual SMS provider/after-commit delivery and B9 integration/
hardening remain. H2 tests exercise the real SecurityFilterChain, Flyway and
Hibernate validation; they do not establish actual MySQL plans or engine behavior.
History detail, filtering/pagination, Reservation update/cancel and payment remain
outside this gate. No shared blocker was found.
