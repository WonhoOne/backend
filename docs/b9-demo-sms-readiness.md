# B9-A2.1 Standalone Demo and Live SMS Readiness

Backend base: `415607855a57e8f41eb00cbcee5b0f671e8ed16f`.
Approved Shared docs/main: `cad8daed210cfb60078f24cabe14c2f383f3ef65`,
[Baseline v0.2](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/baseline/BASELINE-v0.2.md).
Related requirements: FR-01/02/06/07/08/10/11/14, BR-03/04/14/17/21/24/26/27/30/31,
NFR-02/06. Shared Contract changed: **NO**. Final freeze: **NO**.

B9-A1 validated actual MySQL V1–V4, InnoDB, locking and runtime behavior. This gate
adds external one-shot demo provisioning, a standalone runtime and a manual paid
SMS harness. It does not decide final business content. Actual SOLAPI send:
**NOT EXECUTED**. Handset receipt: **NOT VERIFIED**. B9-A2.2 is the next human gate;
B9-B Frontend integration, B9-C Console/Voice and B9-D final E2E remain separate.

## Team/operator decisions and manifest contract

Shared Theme, Style, options and business rules are approved. Exact demo product
names, descriptions, prices, dates, Employee credentials and SMS recipient are
team/operator supplied. Product content is external JSON, never Java or migration
data. Put a local manifest in ignored `.local/demo.json`; do not commit local data.
Only this documentation contains the placeholder template below. It is **not
executable JSON input** until dates and integer amounts are supplied.

```json
{
  "schemaVersion": 1,
  "products": [{
    "key": "<OPERATOR_OWNED_REFERENCE_KEY>",
    "theme": "GOLF_CHALLENGE",
    "name": "<TEAM_APPROVED_PRODUCT_NAME>",
    "description": "<TEAM_APPROVED_DESCRIPTION>",
    "stylePrices": [
      {"style": "CLASSIC", "amount": "<TEAM_APPROVED_POSITIVE_LONG_KRW>", "currency": "KRW"},
      {"style": "GRAND", "amount": "<TEAM_APPROVED_POSITIVE_LONG_KRW>", "currency": "KRW"},
      {"style": "PREMIUM", "amount": "<TEAM_APPROVED_POSITIVE_LONG_KRW>", "currency": "KRW"}
    ]
  }],
  "schedules": [{
    "productKey": "<OPERATOR_OWNED_REFERENCE_KEY>",
    "startDate": "<FUTURE_START_DATE>",
    "endDate": "<END_DATE_ON_OR_AFTER_START>"
  }]
}
```

Runtime requires schemaVersion integer 1, a nonempty products array, and a schedules
array (possibly empty). Keys must be nonblank and unique; productKey references
only this manifest. A key is not a DB column or public field. Theme/Style must be
canonical. Name/description are required with existing 255/2000 bounds. Prices
must be positive JSON integers fitting a Java long, currency exactly KRW, with
each allowed Style exactly once. Existing DTO Bean Validation and
TourProductWriteValidator/TourStylePolicy enforce the existing product rules.
Honeymoon/Parents require GRAND/PREMIUM; Golf/Outdoor additionally require CLASSIC.
Multiple products per Theme are supported.

Dates must be ISO calendar dates, start <= end, and start strictly later than
BusinessDateProvider.today (BUSINESS_TIME_ZONE defaults to Asia/Seoul).
Every created Schedule starts confirmed=false. Unknown properties, unknown enums,
numeric strings/fractions/overflow and trailing JSON fail safely. There are no
customer/profile/contact/password, Employee, Inventory, Reservation, History,
confirmation, event/recipient or secret fields. Do not put personal information
in name/description either. Customers use normal GUI/API signup; no fake History.

## Provisioning flow and safety

`DemoScenarioBootstrap` runs at Order(2), after the unchanged Order(1)
EmployeeBootstrap. Defaults: DEMO_PROVISIONING_ENABLED=false, DEMO_SCENARIO_FILE
empty, DEMO_EXPECTED_DATABASE empty. Disabled startup performs no file/DB operation
from this runner. Enabled startup requires both variables and a readable regular
filesystem JSON file. Parse/validation exceptions do not echo JSON or values.

Reader → full manifest validation → exact `SELECT DATABASE()` match → require
zero Product, Schedule, Reservation, SMS event and SMS recipient rows → existing
Product create service + complete prices → flush → future unconfirmed Schedules
→ flush → commit. All catalog writes join one transaction; later failure rolls
them back. Counts/schema version alone enter the provisioning log. The log says
rows *prepared*, not committed, since commit can still fail.

V2's four Inventory rows and existing accounts (including bootstrap Employee or
Customer without Reservation) are allowed and untouched. Employee bootstrap is
its existing separate transaction; catalog rollback does not remove that account.
Do not run multiple provisioners or another app concurrently: this is exclusive
fresh/disposable setup, not a concurrent migration/merge engine. No update,
delete/recreate, name matching or catalog reset is implemented. Existing catalog
blocks a second enabled start. Disable provisioning after the first success.

Schedules lock subsequent Theme changes under BR-31. Employee GET/PUT can still
manage name/description/prices with the same Theme. To change the manifest's Theme
or overall scenario, provision a fresh demo DB. No endpoint, SecurityConfig change,
DB metadata table, schema migration or cross-repo change is introduced.

## Standalone rehearsal on Windows

Prerequisites: Java 21, Docker Desktop Linux engine, free local port 3308. The
fixed `misterworld-b9-demo` Compose project uses mysql:8.4, localhost-only binding,
database/user `misterworld_demo`, unmistakable local-only passwords and a named
volume. This runtime is separate from B9-A1 verification on port 3307. Record
SELECT VERSION() for the patch actually used; the tag may advance.

```powershell
.\scripts\b9-demo.ps1 db-up  # authenticated healthcheck; retains existing demo volume
.\scripts\b9-demo.ps1 build  # default tests + executable JAR, no live profile
```

1. Create the external manifest from the team-approved scenario. Use future dates
   for the backend's current business date, not stale dates from a presentation.
2. Set JWT_SECRET locally (at least 32 UTF-8 bytes). Set
   EMPLOYEE_BOOTSTRAP_ENABLED=true and EMPLOYEE_LOGIN_ID, EMPLOYEE_PASSWORD,
   EMPLOYEE_NAME, EMPLOYEE_ADDRESS, EMPLOYEE_CONTACT through local environment.
   The script checks missing values; existing bootstrap validates and hashes them.
   Never paste these values into GitHub, logs, screenshots or this runbook.
3. Perform first provisioning:

```powershell
$env:DEMO_PROVISIONING_ENABLED = 'true'
$env:DEMO_SCENARIO_FILE = (Resolve-Path .local/demo.json).Path
.\scripts\b9-demo.ps1 start
```

The start action pins its own local DB endpoint/identity and always forces
SMS_DELIVERY_ENABLED=false. It restores modified process variables in finally.
It never stores JWT/Employee/SOLAPI/recipient secrets and never runs a live test.
Run from the repository root. For direct JAR startup instead of the script, set
DB_URL/DB_USERNAME/DB_PASSWORD plus DEMO_EXPECTED_DATABASE to the exact operator DB
and explicitly keep SMS_DELIVERY_ENABLED=false.

4. Stop the server with Ctrl+C after successful startup. Disable provisioning and
   restart. The named volume preserves catalog IDs and presentation state:

```powershell
$env:DEMO_PROVISIONING_ENABLED = 'false'
$env:EMPLOYEE_BOOTSTRAP_ENABLED = 'false'
.\scripts\b9-demo.ps1 start
```

5. GET /api/v1/tours: plain array ordered id ASC, expected product count/content
   and complete prices. GET /api/v1/tour-schedules: plain array ordered startDate
   then id, reservable=true, recruitment.confirmed=false before reservations.
   GET /api/v1/tour-schedules?tourId=<returned-product-id> verifies filtering.
6. Customer signs up through the normal GUI when integrated, or Backend
   POST /api/v1/auth/signup and /auth/login for standalone API rehearsal. Use local
   test-only credentials/contact. Never add these to the manifest. Login supplies
   Bearer token for POST /api/v1/reservations and own GET detail.
7. Select a returned Schedule and canonical configuration. A General reservation
   with participantCount=3 and PREMIUM_VAN_10 crosses the threshold in one row.
   Expect confirmed=true, one durable SMS event and one recipient PENDING,
   attemptCount=0, provider calls=0 while delivery is disabled. Later Reservations
   do not create a second confirmation. History stays empty for future trips.
8. Employee login can GET /api/v1/employee/tours and PUT same-Theme product details.
   Inventory GET shows V2's four rows. Optional POST /api/v1/employee/inventory adds
   a positive quantity; provisioner never adds stock or deducts it on Reservation.

**Never enable real SMS on a rehearsal DB with existing PENDING outbox rows.**
The production poller recovers all due pending recipients when delivery is
enabled, potentially sending many paid messages. Rehearsal is not live preflight.
Use a separate fresh disposable database for A2.2. Do not merely flip the flag.

Cleanup:

```powershell
.\scripts\b9-demo.ps1 db-down  # container down; presentation volume retained
.\scripts\b9-demo.ps1 db-fresh # removes ONLY fixed demo project's data volume, then starts empty
```

`db-fresh` discards all local demo accounts, catalog, reservations and outbox; export
anything needed beforehand. It never drops an arbitrary DB_URL database. Rebuild
the team manifest and repeat first provisioning after reset.

For an automated **test-only offline** Backend rehearsal on a fresh demo DB:

```powershell
.\scripts\b9-demo-dry-run.ps1
```

This writes its external test-only manifest under target, starts a hidden JAR on
port 18080, verifies first provisioning and disabled-provisioning restart, then
public reads, signup/login, threshold crossing and Employee same-Theme GET/PUT.
It verifies Inventory remains four zero-stock rows and the recipient remains
PENDING/attempts=0. It stops only its own server and restores process environment;
it leaves DB rows for inspection. A second run requires an explicitly fresh DB.
Safe evidence is `target/b9-demo-dry-run.txt`; local logs/manifest stay ignored.

## B9-A2.2 HUMAN / OPERATOR GATE — manual paid SMS

**REQUIRES REAL PAID EXTERNAL SEND. HUMAN/OPERATOR ONLY. DO NOT RUN DURING A2.1.**

Operator must separately verify usable registered SOLAPI sender, account balance,
API access and a handset whose owner consents to exactly one confirmation message.
Do not request or record real credentials in a Codex worker/GitHub handoff. Create
a separate, empty disposable MySQL 8 database and its user outside the app. Do not
use a customer, presentation, rehearsal or B9-A1 test database.

Required environment names, without sample values:

| Gate | Required setting |
| --- | --- |
| B9_LIVE_SMS_ACK | exact `I_UNDERSTAND_THIS_SENDS_ONE_REAL_MESSAGE` |
| B9_LIVE_SMS_EXPECTED_DATABASE | exact disposable DB name |
| B9_SMS_RECIPIENT | one consented handset; normalizes to digits |
| DB_URL / DB_USERNAME / DB_PASSWORD | dedicated actual MySQL 8 access |
| JWT_SECRET | local valid signing key |
| SOLAPI_API_KEY / SOLAPI_API_SECRET | local real provider credentials |
| SOLAPI_SENDER_NUMBER | usable registered sender |
| SMS_DELIVERY_ENABLED / SMS_PROVIDER | exactly true / solapi |
| SOLAPI_BASE_URL | leave unset or official https://api.solapi.com origin |

Initializer validates environment before Spring startup, matches SELECT DATABASE(),
checks actual MySQL 8 (no H2/MariaDB), and refuses **any existing tables before
Flyway**. After V1–V4, the single test additionally checks accounts, catalog,
Reservation/event/recipient counts all zero. It uses runtime test-only product
content, future dates, confirmed=false, and one Customer with the environment
contact. One General Reservation with 3 participants creates one event/recipient.

`SmsDeliveryPoller` is replaced with a mock in this IT, so scheduled recovery/retry
cannot send. The real listener, executor, dispatcher, REQUIRES_NEW delivery and
SolapiSmsSender remain wired. A spy guard delegates the first call to the real
adapter and rejects every later call before network access. No parameterized
live test exists. Connection/request timeouts are fixed 5/10 seconds for the test;
delivery wait is bounded to 30 seconds. Provider reject/network/timeout leaves a
retryable row and fails the test; there is no automatic second send.

Only after the human checks above, on the operator's local machine:

```powershell
.\mvnw.cmd -Plive-sms-smoke verify
```

Use this profile alone. Default test/package excludes B9LiveSmsSmokeIT explicitly;
mysql-integration includes only *MySqlIT; this profile includes exactly
B9LiveSmsSmokeIT. No CI step invokes it and no new repository secret is required.
Environment names/gates have ordinary fake-data tests that never call a provider.

Automated success checks confirmed=true, Reservation/event/recipient counts=1,
SENT, attemptCount=1, sentAt populated, nextAttemptAt null, and a nonblank provider
ID if supplied. SENT means **provider accepted**, not handset delivered. Optional
local `target/b9-live-sms-smoke.txt` records UTC timestamp, MySQL version, counts
and PROVIDER_ACCEPTED, with HANDSET_RECEIVED=MANUAL_PENDING. Record backend HEAD
separately with `git rev-parse HEAD`. Never record phones, sender, keys, secret,
Authorization, message text, raw responses, access tokens or full environment.

Human A2.2 record: backend HEAD, run time/time zone, actual MySQL version,
PROVIDER_ACCEPTED YES/NO, HANDSET_RECEIVED YES/NO, one event, one recipient,
attempts=1. Provider ID optional only if safe; never store phones/credentials.
Verify on the handset that the real composed message includes departure-confirmed
meaning, the test product name and dates. A2.2 is complete only after receipt is
confirmed by that human.

On failure, inspect safe category/DB state and check credentials, sender state,
balance, contact format and network privately. A timeout can mean the provider
accepted but response was lost. Check provider/handset before deciding to repeat.
Do not restart this database with an ordinary SMS-enabled app. For a human-approved
rerun, create another fresh disposable DB and acknowledge again. Preserve safe
evidence, then remove only the explicitly identified disposable resource.

## Provider document review and presentation

Reviewed official SOLAPI docs on 2026-10-02 (Asia/Seoul):
[send API](https://solapi.com/developers/api/messages) and
[API-key HMAC](https://solapi.com/developers/api/authentication-api-key).
Current POST /messages/v4/send-many/detail, HMAC-SHA256 over date+salt using secret,
pre-registered sender and detail acceptance fields agree with the B8 adapter.
The detail response's registeredSuccess/messageList status 2000 represents
acceptance; handset receipt is a separate human observation. No adapter change or
real endpoint call was needed for the document review.

Walkthrough: team manifest → reader/validator → expected DB/empty data guard →
existing product validation/create → prices → future Schedule → public GETs.
Then Reservation threshold → durable outbox → AFTER_COMMIT → executor → dispatcher
→ real sender → provider accepted → recipient SENT → human handset confirmation.

> 실제 상품 내용은 코드에 고정하지 않고 팀에서 확정한 manifest를 실행 시 주입합니다.

## Validation record

Run default test/package, mysql-integration verify, demo Compose/config/startup
and Backend-only HTTP rehearsal. Never run live-sms-smoke as A2.1 validation.
Demo tests cover parsing, price/date/reference validation, all data guards,
multiple products per Theme, complete style sets, reservability and transaction
rollback. Actual MySQL tests also exercise provisioning and later-insert rollback.
Final counts/results and standalone dry-run evidence are recorded with the PR.
Migrations V1–V4 unchanged; no V5. Real provider executions during A2.1: **0**.

Local validation on 2026-10-02 (Asia/Seoul), Java 21.0.11, actual MySQL 8.4.11:

| Gate | Observed result |
| --- | --- |
| Default test / package | 488 tests; failures/errors/skips 0; executable JAR built |
| mysql-integration verify | 488 default + 21 MySQL; failures/errors/skips 0 |
| New ordinary safety coverage | 39 demo tests + 19 live preflight fake-data tests |
| Standalone dry-run | PASS; first JAR bootstrap, disabled-provisioning restart, public GETs, signup/login, Reservation/detail, Employee Inventory/tours GET and same-Theme PUT |
| Offline outbox | 1 Reservation, 1 event, 1 recipient PENDING, attempts 0; provider calls 0 |
| Inventory / migrations | four zero-stock catalog rows retained; V1–V4 unchanged, no V5 |
| Live profile | compiled + static discovery/wiring/privacy audit only; never executed |

The MySQL rollback test injects a failure at the Schedule repository after actual
Product/price writes; the transaction leaves zero catalog rows. It needs no extra
database privilege. The dry-run uses a generated test-only external manifest and
local dummy Employee/Customer credentials, never final team content. Local evidence
and build logs are under ignored target. CI results belong to the submitted PR.
