# Backend Domain Foundation

## Scope and reference

The existing pure Java model preserves its original business-rule foundation.
The current approved reference is
[WonhoOne/docs main Baseline v0.2](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/baseline/BASELINE-v0.2.md),
snapshot `cad8daed210cfb60078f24cabe14c2f383f3ef65`.
Requirements, Domain Model and Business Rules at that snapshot remain the SSOT.

This foundation does **not** implement all v0.2 rules or public API behavior.
B0/B1 adds independent persistence models without rewriting these pure classes.

Related existing foundation requirements: FR-01, FR-03, FR-04, FR-06, FR-07,
FR-08, FR-14. Related rules: BR-01, BR-02, BR-03, BR-04, BR-06, BR-07,
BR-12, BR-13, BR-14. This is partial support, not completion of those requirements.

## Current responsibilities

- `domain`: fixed catalogs, minimal Customer/TourProduct/Reservation models,
  Theme/Style eligibility, and in-memory schedule aggregation/confirmation.
- `application`: adds a Reservation and calls the `SmsSender` port on the
  first confirmation transition.
- `application.port`: provider-independent SMS boundary.
- New `UserRole` and `InventoryItemType` enums express approved shared values;
  they contain no persistence annotations.

`Reservation` delegates the 1..10 base range to `ReservationPartyPolicy`.
`TourSchedule` delegates Theme-specific validation (Honeymoon 2/4/6/8/10), derives
couple/team count, and confirms at two couples/teams or three general participants.
Its collection access returns immutable snapshots. `TourStylePolicy` validates
Theme/Style eligibility. These models have no database identity.

## Known gaps for subsequent work

- B4 implements party/configuration/transport policies, pricing and snapshots; see
  [reservation-domain-persistence.md](reservation-domain-persistence.md). Final create orchestration is B5.
- The minimal pure TourProduct stores only Theme; product identity, name,
  description and style prices now exist in separate JPA models.
- Public recruitment is a defined v0.2 projection; coupleCount is derived rather
  than an independent input/entity. API projection is deferred to B3/B5.
- Price, Loyalty eligibility and historical snapshot persistence are implemented
  by B4; Reservation/History REST and final transaction coordination remain B5/B6.
- B3 implements BR-30 from approved docs/main `c38995d1c33eabc73c2378335a1536b0d9ae4b1e`:
  startDate must be after Backend business date; confirmation does not close intake.
  See [tour-catalog-schedule-api.md](tour-catalog-schedule-api.md). B5 reuses this policy.

## Notification boundary

The existing service is a synchronous foundation example, not the final v0.2
SMS workflow. It neither deduplicates Customer recipients nor isolates delivery
failures or stores retryable notifications. The shared failure policy is already
approved: see the SSOT SMS section. Subsequent confirmation integration must add
transaction/after-commit handling, recipient identity, retry state and a real
provider. These behaviors are not supplied by B0/B1.

## Why preserve the pure model

Business rules remain readable independently of ORM mapping. JPA entities describe
physical data in infrastructure, while later application use cases will explicitly
connect persistence and domain rules. See [backend-architecture.md](backend-architecture.md).
