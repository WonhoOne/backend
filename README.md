# backend
Backend API, database, and business logic for Mister World.

## Stack

- Java 21
- Maven
- Spring Boot 4.1.1

Shared contracts are maintained in `WonhoOne/docs/main`. Backend-only implementation notes are in [`docs/backend-bootstrap.md`](docs/backend-bootstrap.md).

Build and test with the Maven Wrapper:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```
