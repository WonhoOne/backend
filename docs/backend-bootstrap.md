# Backend Bootstrap

## Status and scope

Backend Internal Approved Decision. Shared contracts are defined by
[WonhoOne/docs main v0.2](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/baseline/BASELINE-v0.2.md).
This document records implementation status, not a duplicate API contract.

## Technology decisions

| Item | Value |
| --- | --- |
| Java | 21 |
| Build tool | Maven Wrapper |
| Spring Boot | 4.1.1 |
| Base package | `com.wonhoone.misterworld` |
| Persistence | Spring Data JPA / MySQL / Flyway |
| Tests | Pure Java unit tests; H2 MySQL-mode persistence tests |
| Authentication | Spring Security / Nimbus HS256 JWT; BCrypt; stateless |
| CI | GitHub Actions on main pushes and main PRs |

## Implementation status

B0/B1 provides local architecture documentation and persistence for UserAccount,
TourProduct, style prices, TourSchedule, and the fixed Inventory catalog.
Flyway owns schema creation; Hibernate validates the migrated schema.

Authentication (JWT Bearer), DTOs, common errors, collection ordering without
pagination, price/Loyalty, Inventory semantics, and SMS failure behavior are
approved shared v0.2 contracts. Implementation status:

- B2 implemented: Auth/JWT, password encoding, authorization, Employee provisioning,
  D-10 common auth errors. See [auth-security.md](auth-security.md).
- B3 implemented: public product/schedule reads, Employee product writes,
  DTO/error projection, business date and reservability. See
  [tour-catalog-schedule-api.md](tour-catalog-schedule-api.md).
- B4 implemented: Reservation party/configuration/price rules, Loyalty eligibility,
  snapshot persistence and BR-31 Theme lock. See
  [reservation-domain-persistence.md](reservation-domain-persistence.md).
- B5 implemented: CUSTOMER Reservation REST, ownership, Schedule locking,
  recruitment aggregation/confirmation and internal first-transition boundary.
  See [reservation-api-concurrency.md](reservation-api-concurrency.md).
- B6 implemented: CUSTOMER History REST over Reservation scalar snapshots.
  See [travel-history.md](travel-history.md).
- B7 implemented: EMPLOYEE Inventory REST, fixed catalog reads and atomic adds
  with target-row locking and checked overflow. See [inventory-api.md](inventory-api.md).
- B8 after-commit SMS/provider integration and B9 integration/hardening remain.

The existing pure Domain Foundation remains an incomplete in-memory model;
see [domain-foundation.md](domain-foundation.md) for its limits.

Local startup now also requires JWT_SECRET (at least 32 UTF-8 bytes).
JWT_EXPIRES_IN_SECONDS defaults to 3600; Employee bootstrap is opt-in.
Business date defaults to Asia/Seoul (BUSINESS_TIME_ZONE override).
Inventory uses a write transaction with PESSIMISTIC_WRITE on the target row and
Math.addExact for checked long addition. Backend-local choices still to make include
SMS provider/retry mechanics and deployment. These are implementation decisions,
not unresolved shared contracts.

## Documentation boundary

Shared changes must be approved in WonhoOne/docs first. Physical schema and
package choices live here; see [backend-architecture.md](backend-architecture.md)
and [persistence-foundation.md](persistence-foundation.md).
