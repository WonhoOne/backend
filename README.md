# backend
Backend API, database, and business logic for Mister World.

## Stack

- Java 21
- Maven
- Spring Boot 4.1.1

Shared contracts are maintained in `WonhoOne/docs/main`. Backend-only implementation notes are in [`docs/backend-bootstrap.md`](docs/backend-bootstrap.md) and [`docs/domain-foundation.md`](docs/domain-foundation.md).

Package responsibilities and persistence setup are described in
[`docs/backend-architecture.md`](docs/backend-architecture.md) and
[`docs/persistence-foundation.md`](docs/persistence-foundation.md).
Local application startup requires an existing MySQL database and credentials
provided through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, plus `JWT_SECRET`
(at least 32 UTF-8 bytes). Authentication and optional Employee provisioning are
documented in [docs/auth-security.md](docs/auth-security.md).
Tests use the H2 test profile and require no external MySQL server.
Product and schedule APIs, business date configuration (`BUSINESS_TIME_ZONE`,
default Asia/Seoul), and the presentation walkthrough are documented in
[docs/tour-catalog-schedule-api.md](docs/tour-catalog-schedule-api.md).
B4 domain rules, snapshots, Loyalty and the B5/B6 integration boundaries are in
[docs/reservation-domain-persistence.md](docs/reservation-domain-persistence.md).
Reservation REST, ownership, Schedule locking and recruitment/confirmation are in
[docs/reservation-api-concurrency.md](docs/reservation-api-concurrency.md).

Build and test with the Maven Wrapper:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```
