# Durable Schedule confirmation SMS (B8)

Backend-local implementation for FR-08/14, BR-14/27 and NFR-02/05/06. Approved
shared source: [Baseline v0.2](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/baseline/BASELINE-v0.2.md)
and its [SMS contract](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/api/api-spec-draft.md#9-sms-confirmation-integration).
Backend starting main: `dae2152dd7b1738afd8402438739402ebfc049bd`.
Shared Contract changed: **NO**. B8 implemented; B9 integration/hardening remains.

## Two transactions

```text
Transaction A — Business (ReservationCommandService.create, READ_COMMITTED)
  authenticated Customer → target Schedule PESSIMISTIC_WRITE
  → reservability/configuration/price/Loyalty → Reservation saveAndFlush
  → persisted participant SUM → threshold → markConfirmed false→true
  → Schedule flush → ScheduleConfirmationOutboxService.capture (MANDATORY)
      → DISTINCT Customer id/current contact → compose current Product name/dates
      → event + recipient rows → flush → publish SmsConfirmationOutboxReady
  → response projection → COMMIT

AFTER_COMMIT — SmsConfirmationAfterCommitListener.onReady
  → submit to smsDeliveryExecutor → SmsDeliveryDispatcher.dispatchEvent

Transaction B — Delivery (SmsDeliveryAttemptService.deliver, REQUIRES_NEW)
  recipient PESSIMISTIC_WRITE → skip SENT/not due
  → stored contact + stored event message → SmsSender.send → SOLAPI
  → accepted: SENT, attemptCount++, sentAt, optional providerMessageId, nextAttemptAt=null
  → failure: PENDING, attemptCount++, nextAttemptAt=now+backoff
  → flush → COMMIT

Recovery — SmsDeliveryPoller.poll
  → SmsDeliveryDispatcher.dispatchDue → due recipient IDs → Transaction B per ID
```

Business commit includes both Reservation/confirmation and its durable notification
intent. An **outbox persistence failure** rolls all of them back through the existing
INTERNAL_ERROR path. An **external provider failure** occurs after business commit
and cannot roll back Reservation/confirmation. Delivery holds only the recipient
lock during network I/O, never the original Schedule lock or business transaction.

The after-commit listener explicitly submits to a two-thread executor with a queue
of 50. Submission rejection and task failures are caught with safe categories;
they do not turn a committed POST into an error. No network work runs on the POST
thread. The signal is an acceleration hint: crash, rejection or shutdown can lose
it without losing the DB rows. Scheduled polling recovers them after restart.
No Reservation POST is replayed or automatically retried.

## Physical outbox and immutable snapshots

V4__create_sms_confirmation_outbox.sql adds two tables. V1/V2/V3 are immutable.
Flyway current version and applied migration count are **4**; Hibernate validates
the same scripts in fresh H2 MySQL mode. UTC JDBC time conversion is configured.

| Table | Columns and constraints |
| --- | --- |
| sms_confirmation_event | BIGINT identity id; tour_schedule_id FK + UNIQUE; message_text VARCHAR(2000); created_at TIMESTAMP(6), all required |
| sms_confirmation_recipient | BIGINT identity id; confirmation_event_id FK; customer_id FK; contact_snapshot VARCHAR(255); status VARCHAR(16); attempt_count INT; nullable next_attempt_at/sent_at TIMESTAMP(6), provider_message_id VARCHAR(255) |

Recipient UNIQUE(confirmation_event_id, customer_id) also supports event lookups
through its leading column; no redundant event-only index is needed. The due
index is (status, next_attempt_at, id). Checks restrict status to PENDING/SENT,
nonnegative attempts, PENDING with next time and no sent time, and SENT with sent
time and no next time. There is no terminal DEAD state or retry limit. Attempts
saturate at INT_MAX rather than becoming unretryable on diagnostic overflow.

B5's Schedule lock serializes the first transition; the unique Schedule event
constraint is an additional DB defense. Later Reservations on an already-confirmed
Schedule create no event and add nobody to the existing recipient set.

ReservationJpaRepository.findConfirmationRecipients selects scalar DISTINCT
Customer identity + current stored contact, ordered by Customer id. The triggering
Reservation has already been flushed. Several Reservations by one Customer yield
one row; two Customers sharing a contact yield two rows. Dedup is by identity,
never by contact. Delivery does not dereference a current Customer profile.

ScheduleConfirmationMessageComposer preserves the full transition-time Product
name and Schedule start/end dates in a Korean departure-confirmed message. It
stores no price, Customer name/address/login, Reservation id, credential or token.
Contact and message are immutable event-time snapshots: subsequent profile,
Product name or Schedule date changes do not alter retries. Provider normalization
does not mutate the stored contact.

## Retry, failure isolation and dedup limits

Polling selects PENDING rows with nextAttemptAt <= the qualified UTC smsClock,
ordered by id, at most batchSize per poll. Event dispatch uses bounded keyset
batches to cover every recipient of that event. There is no batch-wide delivery
transaction. Each call crosses a separate Spring service proxy to REQUIRES_NEW.
Unexpected recipient transaction failures are caught per ID so following IDs run.

SmsRetryPolicy uses capped exponential delays: 30, 60, 120, 240, 480, 960, 1800,
then 1800 seconds by default. It avoids overflowing multiplication or unbounded
loops. Failure stays PENDING indefinitely. Initially nextAttemptAt equals creation
time; successful rows never return to PENDING. Disabled delivery creates outbox
rows but activates no provider, listener, poller or delivery worker.

The recipient lock is acquired before checking SENT/due and before provider I/O.
Two workers may discover the same ID, but only one can send concurrently; after
the first commits SENT the second skips. These constraints minimize successful
duplicates. They do **not** provide distributed exactly-once delivery:

```text
Provider accepts → process/network/DB failure before SENT commit → later retry
```

In that ambiguous interval a duplicate external send is possible. An HTTP timeout
can also hide provider acceptance. No unsafe compensation, distributed transaction
or invented provider idempotency key is claimed. SENT means provider acceptance,
not guaranteed arrival at the handset. There is no public delivery status/API.

## SOLAPI adapter and official protocol

SOLAPI is a replaceable **Backend-local** choice, not a Shared Contract requirement.
Application code depends on SmsSender, SmsMessage and SmsSendResult; only the
infrastructure.sms.solapi package knows the protocol. No SMS SDK or new dependency
is introduced: Java 21 HttpClient and existing Jackson 3 perform the REST request.

Official references checked on 2026-10-01:
[message sending](https://solapi.com/developers/api/messages),
[API-key authentication](https://solapi.com/developers/api/authentication-api-key).

One recipient attempt POSTs /messages/v4/send-many/detail with one message
{to, from, text} and showMessageList=true. Common phone formatting separators are
removed at the adapter boundary; other invalid stored contacts become retryable
internal failures. The configured sender must be registered with SOLAPI. Type is
omitted to allow automatic SMS/LMS detection without truncating Product names.

SolapiAuthHeaderFactory signs UTF-8 (UTC ISO-8601 date + a fresh 16-byte SecureRandom
hex salt) with HmacSHA256 and the secret; signature hex is lowercase. Authorization
has HMAC-SHA256 apiKey=..., date=..., salt=..., signature=.... The raw secret is
never transmitted in the header/body. Startup restricts configured origins to
HTTPS; redirects are not followed. Connect/request timeouts are 5/10 seconds.

HTTP 2xx alone is insufficient. Acceptance requires an empty failedMessageList,
group count registeredSuccess=1/registeredFailed=0, and exactly one entry in the
send response's **array** messageList with statusCode="2000". If a to field is
returned it must match the request. An available valid messageId is stored.
Malformed/missing/unexpected responses, non-2xx/auth errors, network failures and
timeouts are safe SmsDeliveryException categories. Raw responses/causes are not
attached to exceptions. Logs contain recipient/event IDs, attempts and fixed
categories, never contacts, text, credentials, Authorization or profile data.

## Configuration and real demo setup

| Environment | Default / purpose |
| --- | --- |
| SMS_DELIVERY_ENABLED | false; enable for final demo/production |
| SMS_PROVIDER | solapi |
| SMS_DELIVERY_POLL_DELAY_MS | 10000 |
| SMS_DELIVERY_BATCH_SIZE | 50 |
| SMS_RETRY_INITIAL_SECONDS / SMS_RETRY_MAX_SECONDS | 30 / 1800 |
| SOLAPI_API_KEY / SOLAPI_API_SECRET | required only when enabled; environment secrets |
| SOLAPI_SENDER_NUMBER | required registered sender; no tracked real number |
| SOLAPI_BASE_URL | https://api.solapi.com |
| SOLAPI_CONNECT_TIMEOUT_SECONDS / SOLAPI_REQUEST_TIMEOUT_SECONDS | 5 / 10 |

Enabled startup fails safely for missing credentials/sender or an unsupported
provider. Properties diagnostic strings redact provider configuration. Maven
Surefire forces the test JVM system property sms.delivery.enabled=false, which
outranks inherited OS SMS_DELIVERY_ENABLED=true and profile config data.
Explicit Spring test properties still outrank that system property, allowing
the intentionally enabled B8 tests to use their mocked SmsSender.
CI uses fake sender/local HTTP only and sends no paid messages. No skipped live
JUnit test is added.

Final demo/B9 operator steps:

1. Set existing DB_URL/DB_USERNAME/DB_PASSWORD and JWT_SECRET; apply/validate V4.
2. Register a sender in the SOLAPI Console and prepare account balance/API access.
3. Supply SOLAPI_API_KEY, SOLAPI_API_SECRET, SOLAPI_SENDER_NUMBER through environment
   secrets, SMS_PROVIDER=solapi and SMS_DELIVERY_ENABLED=true. Never show secrets in
   screenshots, source code or presentation output.
4. Use a controlled demo database and consented handset contacts; enabling delivery
   also recovers previously pending rows. Create a future Schedule and submit valid
   Reservations crossing its threshold, then verify real text receipt and internal
   provider acceptance trace. Later Reservations must not trigger new sends.
5. Verify failure/recovery, restart recovery and sender permissions in B9. Real
   credentials/handset smoke is pending; adapter contract tests do not claim it.

## Tests, presentation and B9

SmsOutboxIntegrationTests covers recipients, snapshots, disabled delivery, atomic
commit/rollback, insertion-failure rollback, mandatory transaction and DB dedup.
SmsAfterCommitIntegrationTests exercises the actual transactional listener and
executor, including a slow/failing fake provider and the real Reservation POST.
Delivery/retry tests cover per-recipient outcomes, backoff, due timing, batching,
recovery without new Reservations, immutable send inputs and SENT exclusion.
Concurrency tests prove competing workers serialize and competing Reservations
create one event. SOLAPI tests use local JDK HttpServer for wire requests, accepted
and malformed/rejected responses, auth errors, timeout/network errors; HMAC has a
fixed known signature test. Startup and executor rejection are also tested.

The verified starting baseline is 357 tests. B8 adds 73 (outbox 17, after-commit 4,
delivery 12, concurrency 2, retry 4, signal/dispatcher 2, startup 6, SOLAPI HTTP 24,
HMAC 2), including the Surefire-system-property versus enabled-environment regression.
Full Maven Wrapper test passes **430 tests, failures 0, errors 0, skipped 0**.
Maven Wrapper package also passes 430 tests and builds the executable JAR.
Migration validation and staged/worktree diff checks pass. Tests never invoke
the real provider.

H2 establishes test behavior, not MySQL engine equivalence. B9 must verify actual
MySQL 8 V4 DDL, TIMESTAMP(6)/UTC, CHECK/FK/unique/index behavior, InnoDB recipient
locking/waits, listener timing and lock duration around network calls. Real SOLAPI
credential/handset smoke remains B9/demo setup. No Shared blocker was found.

For presentation, show ReservationCommandService → outbox service → DISTINCT query
→ V4 constraints → AFTER_COMMIT listener/executor → dispatcher → delivery
transaction/lock → SmsSender → SOLAPI adapter → retry policy/poller. Explain:

> “문자 API가 실패해도 예약 확정 자체가 취소되면 안 되기 때문에 외부 API 호출을
> 예약 transaction 밖으로 분리했습니다. 확정 transaction 안에서 발송할 대상을
> DB에 먼저 저장하므로 서버가 잠깐 내려가도 나중에 다시 보낼 수 있습니다.”

The pure TourScheduleReservationService remains a synchronous **legacy foundation**;
only its port use/tests were made compile-compatible. The final persisted path
never calls it. Public REST DTOs/errors/security paths are unchanged. No frontend,
ai-console, shared docs, Inventory coupling, cancellation, payment or profile
schema changes belong to B8.

## B9-A1 runtime validation

The separate actual-MySQL harness, scenario tests and current validation record
are documented in [B9-A1 MySQL runtime hardening](b9-mysql-runtime-hardening.md).
This verifies the current approved v0.2 snapshot; it is not a final freeze.
Real SOLAPI/handset smoke remains B9-A2 and cross-repository E2E remains B9-B/C/D.

The [B9-A2.1 standalone demo and live smoke runbook](b9-demo-sms-readiness.md)
provides the manual, operator-gated harness for B9-A2.2. Never enable real delivery
on a rehearsal DB with pending recipients; use a separate fresh disposable DB.
Actual SOLAPI send and handset receipt remain unverified until that human gate.
