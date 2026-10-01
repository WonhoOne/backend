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
Customer Travel History snapshot projection and its presentation walkthrough are in
[docs/travel-history.md](docs/travel-history.md).
Employee Inventory reads and atomic additions, row locking and the presentation
walkthrough are in [docs/inventory-api.md](docs/inventory-api.md).
B8 durable SMS confirmation, SOLAPI setup and its presentation walkthrough are in
[docs/sms-confirmation-delivery.md](docs/sms-confirmation-delivery.md).
Real delivery defaults off (`SMS_DELIVERY_ENABLED=false`); outbox capture remains on.
B8 implemented; B9 integration/hardening remains.

Build and test with the Maven Wrapper:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

## B9-A1 runtime validation

The separate actual-MySQL harness, scenario tests and current validation record
are documented in [B9-A1 MySQL runtime hardening](docs/b9-mysql-runtime-hardening.md).
This verifies the current approved v0.2 snapshot; it is not a final freeze.
Real SOLAPI/handset smoke remains B9-A2 and cross-repository E2E remains B9-B/C/D.
