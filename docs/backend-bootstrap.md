# Backend Bootstrap

## Status and scope

Backend Internal Approved Decision. Shared contracts are defined by
[WonhoOne/docs main v0.2](https://github.com/WonhoOne/docs/blob/79955fc9c864ad0efce6dee9db2319e684573e7a/baseline/BASELINE-v0.2.md).
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
| CI | GitHub Actions on main pushes and main PRs |

## Implementation status

B0/B1 provides local architecture documentation and persistence for UserAccount,
TourProduct, style prices, TourSchedule, and the fixed Inventory catalog.
Flyway owns schema creation; Hibernate validates the migrated schema.

Authentication (JWT Bearer), DTOs, common errors, collection ordering without
pagination, price/Loyalty, Inventory semantics, and SMS failure behavior are
approved shared v0.2 contracts. Their implementation is still pending:

- B2: Auth/JWT, password encoding, authorization, Employee provisioning.
- B3: product/schedule public and Employee API, DTO/error projection.
- B4/B5: final Reservation rules, configuration/price snapshots, concurrency,
  History/Loyalty, confirmation and SMS after commit.
- Later use cases: atomic Inventory addition and provider integration.

The existing pure Domain Foundation remains an incomplete in-memory model;
see [domain-foundation.md](domain-foundation.md) for its limits.

Backend-local choices still to make include JWT library/claims/lifetime,
provisioning mechanism, business clock, SMS provider/retry mechanics,
transaction/locking strategy, and deployment. These are implementation decisions,
not unresolved shared contracts.

## Documentation boundary

Shared changes must be approved in WonhoOne/docs first. Physical schema and
package choices live here; see [backend-architecture.md](backend-architecture.md)
and [persistence-foundation.md](persistence-foundation.md).
