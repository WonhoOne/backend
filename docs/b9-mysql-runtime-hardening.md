# B9-A1 Actual MySQL Runtime Hardening

This gate verifies correctness against the current approved v0.2 contract. It is
not a final freeze. Future approved contract changes require revalidation.

Backend starting main: `b6cdd38d0e0620d35f545d34bc3ccbb875461bff`.
Approved Shared docs/main: `cad8daed210cfb60078f24cabe14c2f383f3ef65`,
[Baseline v0.2](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/baseline/BASELINE-v0.2.md).
Shared Contract changed: **NO**. Related requirements: FR-01/06/08/09/11/14/15,
BR-14/16/21/23/24/26/27/30, NFR-02/06. Existing REST paths, DTOs, error codes and
business meanings are unchanged. No product/schedule demo catalog is decided here.

## Reproduce locally

Use Java 21 and Docker Desktop with its Linux engine running. The Compose project
`misterworld-b9-mysql` binds only `127.0.0.1:3307`, uses `mysql:8.4`, and has a
database/user named `misterworld`. All passwords and the JWT key in the harness
are explicitly test-only. This environment is never a production/demo setup.

```powershell
.\scripts\b9-mysql.ps1 up       # Wait for authenticated SELECT 1 healthcheck
.\scripts\b9-mysql.ps1 verify   # 430 H2 tests, package, then separate MySQL IT
.\scripts\b9-mysql.ps1 down     # Remove this disposable project's container/data
```

For a second run, reset only this project's test data first:

```powershell
.\scripts\b9-mysql.ps1 fresh    # down -v, then up --wait
.\scripts\b9-mysql.ps1 verify
```

There is no persistent named volume. The fixed Compose project reset removes its
container and anonymous data volume, and never sends DROP DATABASE to an arbitrary
DB_URL. Verify overrides inherited DB settings with the local Compose endpoint and
restores process environment settings in finally. Failure exits as a PowerShell
error. A caller can use an ephemeral CI-equivalent MySQL service instead by setting
DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET and B9_MYSQL_TEST_DATABASE=misterworld,
then running `.\mvnw.cmd -Pmysql-integration verify`. Use an empty disposable DB.
The test initializer checks the database name, actual MySQL 8 engine and zero
existing tables before Flyway or application startup runners can modify anything.
Existing databases are refused; the test never cleans or resets migration history.

Local/CI JDBC options disable TLS only for the local test service, allow test-user
public-key retrieval, and force the connection/session to UTC. Connect/socket and
InnoDB session lock waits are bounded. Global isolation and strict SQL mode remain
unchanged. These connection options are not new production requirements.

## Maven and SMS safety

Default `mvnw.cmd test` and `mvnw.cmd package` still run the **430-test H2 suite**
without MySQL. Surefire explicitly excludes `*MySqlIT.java`. The opt-in
`mysql-integration` profile binds Failsafe integration-test + verify, includes
only that suffix, and fails if no IT is found. There is no silently skipped
MySQL validation or H2 fallback. ITs run sequentially with a single reusable JVM.

Failsafe sets `sms.delivery.enabled=false` and `employee.bootstrap.enabled=false`
as JVM system properties; the MySQL Spring tests also explicitly force both off.
Inherited enabled OS settings cannot activate SOLAPI. No SmsSender is present in
the MySQL context. Outbox capture remains active and is verified. **Real provider
calls, paid sends and handset verification: 0.** The SMS lock test uses the real
repository boundary and persists SENT directly, with an unmistakably test-only
provider ID. It does not enable a delivery worker or send a message.

The Failsafe JVM deliberately uses Pacific/Honolulu, while Hibernate JDBC and
MySQL connections use UTC. TIMESTAMP(6) tests compare microsecond-compatible
Instant values, including created_at, next_attempt_at and sent_at. A separate
SQL session-zone +09:00 read verifies that wall-clock representation changes
while UNIX_TIMESTAMP preserves the instant; the session is restored in finally.

## Scenarios and physical inspection

`MySqlIntegrationSupport.EmptyTestDatabase` proves fresh pre-migration state.
The inherited BeforeAll verifies the exact four V2 zero-stock rows before any
fixture cleanup. Tests clean children before parents in a transaction, retain
Flyway history and Inventory identities, and restore quantities after scenarios.

`MySqlRuntimeMySqlIT` checks successful versions 1/2/3/4, latest version 4,
Flyway validation and startup with Hibernate ddl-auto=validate. It inspects
information_schema, SHOW CREATE TABLE, types/nullability/PK/FK/index metadata,
enforced CHECK/FK/UNIQUE errors and SMS PENDING/SENT combinations. Constraint
tests assert MySQL vendor error codes, so unrelated SQL syntax or connection
errors cannot masquerade as constraint enforcement. BIGINT prices exceed the
32-bit range; confirmation exercises false-to-true boolean round trips.

Employee bootstrap runs against the real MySQL repositories, validates and hashes
test-only credentials, executes twice without duplicates and logs in using a real
JWT. Invalid enabled configuration rejects before writing an account. The runner
is invoked in a transaction as a second-startup equivalent; existing default
EmployeeBootstrapStartupTests retain actual startup-runner configuration coverage.
The Backend-only HTTP smoke covers signup/login, public tours/schedules,
Reservation create/detail, completed History and Employee Inventory read/add,
using real production controllers and the SecurityFilterChain. This is not a
Frontend or Employee Console E2E test.

`MySqlConcurrencyMySqlIT` follows the production services and repositories:

- Two Schedule creates yield two rows, SUM=4, counts 2 and 4, one first transition,
  one event and one recipient for two Reservations by the same Customer.
- A held Schedule transaction writes the first Reservation before the second
  Customer lookup. An AOP observer records the real lookup and JDBC READ_COMMITTED isolation
  before release. The second create waits, then sees the committed first row and
  creates one event for both distinct Customers. Server default remains
  REPEATABLE-READ; it is never relaxed to make this scenario pass.
- An already confirmed future Schedule accepts another Reservation without
  another event or extra recipients for the old event.
- Concurrent Inventory +5/+7 yields 12. A held item lock blocks the same-item
  writer while GOLF_BALL remains independent.
- A held SMS recipient lock blocks the competing repository transaction; after
  commit it reads SENT and not-due. No external send is involved.

Latches, Future.get, transaction lock waits and executor termination are bounded;
release/shutdown happen in finally. These are small correctness scenarios,
not stress tests, throughput guarantees or production-scale benchmarks.

## Travel History query plan

The test asserts `ix_reservation_customer_end_date` column order, creates 501
historical Reservations (one target Customer, 500 another), runs ANALYZE TABLE,
captures EXPLAIN for the same scalar projection/predicate/order as the production
repository, and executes the real repository projection. It never forces an index
or asserts a particular optimizer choice. The evidence includes query, possible_keys,
chosen key, estimated rows and Extra. Raw DDL/EXPLAIN remains in Backend-local
test artifacts, not shared public API documentation.

## CI and run evidence

Existing `build-and-test` remains intact. `mysql-integration` adds a fresh
mysql:8.4 service on every PR targeting main and push to main (also manual dispatch).
It needs no repository secrets and uses Java 21, a service healthcheck and the
same Maven profile. Reports are uploaded even on failure:
`target/failsafe-reports/` and `target/b9-mysql-runtime.txt`.
Safe diagnostics record VERSION(), version_comment, isolation, session/global/system
time zones, strict SQL mode, collation, DDL and EXPLAIN. Passwords, tokens, profile
data and JDBC credential strings are not added to the runtime evidence.

Local validation completed on **2026-10-02 (Asia/Seoul)**, with full profile verify
ending at 00:12:35 KST. Docker CLI 29.6.1 / Compose v5.2.0 were available; Docker
Desktop's Linux engine was started before validation. Actual version:
**MySQL 8.4.11, MySQL Community Server - GPL**, from SELECT VERSION()/version_comment.
The image tag is mysql:8.4 and may resolve to a newer patch later; every run records
its own version. CI run IDs/conclusions belong in the PR checks and Backend #8
handoff, separately from this local run record.

The first [PR #17 CI run](https://github.com/WonhoOne/backend/actions/runs/36883169099)
verified head `9781b2f2caf03e46a311ba0d5aef1bfd6aab876c`: both H2 and MySQL jobs
passed, CI MySQL was also 8.4.11, and all 430 default / 19 MySQL tests had zero
failures/errors/skips. Its History EXPLAIN matched the local result below.
The new report-upload action was then moved from v4 to current v7 after that
run reported the older action's Node 20 deprecation. Current submitted-head checks
are available on [PR #17](https://github.com/WonhoOne/backend/pull/17); the final
CI record is included in the cumulative Backend #8 handoff.

| Verification | Observed local result |
| --- | --- |
| Default regression | 430 tests, failures 0, errors 0, skipped 0 |
| MySQL integration | 19 tests (6 concurrency, 13 runtime), failures 0, errors 0, skipped 0 |
| Maven test / package / profile verify | PASS; executable Spring Boot JAR built |
| Fresh reset + startup | PASS; authenticated healthcheck, zero pre-existing tables before Flyway |
| Flyway / Hibernate | V1/V2/V3/V4 success, latest 4, checksum validation PASS, ddl-auto=validate PASS |
| Engine / default isolation | All nine application tables InnoDB; REPEATABLE-READ retained |
| Time zones | JDBC session +00:00; global SYSTEM; system UTC; IT JVM Pacific/Honolulu |
| SQL mode | ONLY_FULL_GROUP_BY, STRICT_TRANS_TABLES, NO_ZERO_IN_DATE, NO_ZERO_DATE, ERROR_FOR_DIVISION_BY_ZERO, NO_ENGINE_SUBSTITUTION |
| Collation / canonical login | utf8mb4_0900_ai_ci; trim/lowercase signup duplicate and login PASS |
| CHECK / FK / UNIQUE | Enforced; vendor codes 3819 / 1452 / 1062; invalid SMS states rejected |
| Types / keys | BIGINT/VARCHAR/BOOLEAN and required PK/FK/nullability PASS; SMS TIMESTAMP(6) precision 6 |
| Reservation | Two rows, aggregate 4, one transition/event; same Customer dedup and two distinct Customers covered |
| Reservation lock / isolation | Waits until prior commit; real pre-lock account lookup observed; JDBC READ_COMMITTED; post-wait SUM=4 |
| Confirmed future Schedule | Additional Reservation accepted; no second event |
| Inventory | Concurrent +5/+7=12; same-item waits; independent GOLF_BALL=9 |
| SMS recipient | Competing repository worker waits, then observes SENT/not-due; provider calls 0 |
| UTC timestamps | created_at/next_attempt_at/sent_at Instant round trips PASS; +09:00 SQL display preserves epoch |
| Employee / Auth / HTTP | Bootstrap create-once/hash/login and invalid-config rejection PASS; Customer signup/JWT and Backend-only journey PASS |
| Physical inspection | JDBC information_schema/SHOW CREATE/EXPLAIN and direct container mysql SELECT/SHOW INDEX/SHOW CREATE PASS |
| Migration / diff audit | V1–V4 unchanged, V5 absent; git diff --check PASS |

History EXPLAIN for the 501-row fixture: Reservation access `range`, possible keys
`ix_reservation_customer_end_date, ix_reservation_schedule`, chosen key
`ix_reservation_customer_end_date`, estimated rows **1**, Extra **Using index condition;
Backward index scan**. Schedule access `eq_ref`, chosen key PRIMARY, estimated rows
**1**, Extra **Using where**. The query is shown in the test and runtime artifact.
No obvious pathological plan was observed for this selective fixture; no new index
is justified by this evidence and no benchmark claim is made.

During repeated validation, two existing H2 AFTER_COMMIT scenarios intermittently
timed out when using a real wall clock. Their mocked-delivery test now uses a fixed,
microsecond-compatible smsClock for capture and dispatch. The real executor,
transaction listener, repositories and mocked sender remain exercised; no wait
was lengthened and no assertion was removed. Production SMS behavior is unchanged.
MySQL test-development failures were corrected in test-only metadata filtering
(exclude Flyway's own second-precision timestamp) and query observation (place the
AOP observer before Spring Data's terminal query interceptor). The final complete
verify passed; no production runtime or schema incompatibility was found.

V1/V2/V3/V4 are immutable and unchanged. No V5 has been justified or added.

For the READ_COMMITTED rationale, see the official
[MySQL consistent-read documentation](https://dev.mysql.com/doc/refman/8.4/en/innodb-consistent-read.html).
For the opt-in lifecycle, see the official
[Maven Failsafe integration-test goal](https://maven.apache.org/surefire/maven-failsafe-plugin/integration-test-mojo.html).

## Presentation walkthrough and remaining gates

Start with the default H2 suite: fast, repeatable business/contract/persistence
regression. Then follow EmptyTestDatabase → Flyway V1–V4 → Hibernate validate →
schema/constraints → the three named InnoDB lock scenarios → History EXPLAIN.
Show ReservationCommandService's Schedule lock, saveAndFlush, SUM and first
confirmation; show InventoryCommandService's target-item lock; show
SmsConfirmationRecipientJpaRepository.findByIdForDelivery and the durable state.
The method names above lead directly to executable evidence.

Residual risks: no throughput/load/deadlock-recovery guarantee; provider acceptance
versus SENT-commit ambiguity remains as documented in B8; fixture EXPLAIN is basic
query-plan sanity, not a production workload study. MySQL's collation equivalences
beyond canonical login trim/lowercase are observed rather than assigned new login
semantics. Local test transport/credentials are unsuitable for deployment.

B9-A2.1 provides [external demo provisioning and the standalone/live smoke runbook](b9-demo-sms-readiness.md).
Demo content remains team supplied; this is not a final freeze. B9-A2.2 owns the
human/operator real SOLAPI + handset gate, which has not been executed here.
B9-B owns Frontend live adapters/integration and observed
CORS blockers. B9-C owns AI Console/Voice integration. B9-D owns final cross-repo
E2E/release hardening. Frontend, ai-console and Shared docs are read-only here.
