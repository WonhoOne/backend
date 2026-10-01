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
provided through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
Tests use the H2 test profile and require no external MySQL server.

Build and test with the Maven Wrapper:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```
