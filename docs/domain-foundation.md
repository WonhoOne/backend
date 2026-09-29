# Backend Domain Foundation

## Scope

This backend-internal implementation provides the pure Java domain rules aligned with the shared v0.1.2 baseline. It does not add persistence or public API behavior.

## Shared Contract References

Approved contract source: `WonhoOne/docs` on `main`, latest Baseline v0.1.2.

- `baseline/BASELINE-v0.1.2.md`
- `requirements/requirements.md`
- `requirements/domain-model.md`
- `requirements/business-rules.md`

Related requirements: FR-01, FR-03, FR-04, FR-06, FR-07, FR-08, FR-14.

Related business rules: BR-01, BR-02, BR-03, BR-04, BR-06, BR-07, BR-12, BR-13, BR-14.

## Internal Structure

- `domain`: Theme and TourStyle catalogs, minimal Customer / TourProduct / Reservation representations, schedule aggregation and confirmation policy, and Theme/TourStyle eligibility policy.
- `application`: coordinates adding a reservation and requests confirmation notifications after the domain reports its first confirmation transition.
- `application.port`: `SmsSender`, a provider-independent application boundary.

## Domain Responsibility

- `Reservation` enforces the generic `participantCount >= 1` invariant; it does not know its Theme.
- `TourSchedule` validates Honeymoon's Theme-specific pair invariant before adding a Reservation, and owns its reservation collection, actual total participant count, recruitment threshold, and one-time first-confirmation transition.
- A valid Honeymoon Reservation has an even `participantCount >= 2`. Its couple/team count is derived as `participantCount / 2`; a Honeymoon schedule confirms at 2 or more derived couples/teams. No Couple/Team Entity is used.
- `totalParticipantCount` remains the count of actual participants. Whether the public API exposes `coupleCount` as a field remains TBD for API v0.2.
- `TourStylePolicy` is the single place that validates the approved Theme/TourStyle eligibility combinations.
- Collections exposed by `TourSchedule` are immutable snapshots. The domain is in-memory and has no persistence identity.

## Notification Boundary

`SmsSender` is an application port, not an SMS provider implementation. On the first confirmation transition, the application service requests a notification to each reservation customer's contact currently on the schedule. Provider selection and delivery failure behavior remain undecided.

## Deferred

- JPA, persistence, and database schema
- REST API and DTOs
- Authentication and security
- Detailed TourConfiguration options
- Inventory behavior
- Employee model
- Pricing and Loyalty details
- SMS provider, retry, and failure policy
- Reservation status and cancellation
