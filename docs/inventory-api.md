# Employee Inventory and atomic add (B7)

Backend-local implementation for FR-11, BR-12/24/25/29 and NFR-02/03/06.
Approved shared source: [Baseline v0.2](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/baseline/BASELINE-v0.2.md),
[Inventory / D-10 contract](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/api/api-spec-draft.md)
and [business rules](https://github.com/WonhoOne/docs/blob/cad8daed210cfb60078f24cabe14c2f383f3ef65/requirements/business-rules.md).
Backend base: `5f0ecdf58aa6be7efab5024f43a30f012a166e3d`.
Shared Contract changed: **NO**. Public meanings remain in the SSOT.

## Fixed catalog and GET walkthrough

V2__seed_inventory_catalog.sql creates one zero-stock aggregate for each
COUPLE_TSHIRT, GINSENG_GIFT, GOLF_BALL and SCARF. These are permanent catalog
records, not demo fixtures. V1's unique item_type and nonnegative BIGINT checks
remain intact. Neither GET nor POST creates or repairs catalog rows.

GET /api/v1/employee/inventory follows EmployeeInventoryController →
InventoryQueryService (read-only transaction) →
InventoryJpaRepository.findAllByOrderByIdAsc → InventoryResponse.from → plain array.
Ordering is in the DB query, not a controller/service sort. Zero-stock rows remain
visible. Explicit response projection contains only id, itemType and quantity;
JPA entities never enter public JSON. There are no filters, sorting parameters,
pagination, envelope or path-ID endpoints.

## POST presentation walkthrough

Read InventoryCommandService.add from top to bottom:

1. Existing Spring Security /api/v1/employee/** rule requires EMPLOYEE.
2. EmployeeInventoryController validates InventoryAddRequest and delegates.
3. InventoryCommandService opens a write transaction.
4. InventoryJpaRepository.findByItemTypeForUpdate obtains PESSIMISTIC_WRITE on
   the existing target aggregate row using its unique item_type lookup.
5. A missing canonical row fails with IllegalStateException: the fixed catalog
   invariant is broken, so no replacement row or public not-found code is created.
6. InventoryJpaEntity.addQuantity requires amount > 0 and assigns only the
   successful result of Math.addExact(current quantity, amount).
7. repository.flush makes update failure occur inside the transaction.
8. InventoryResponse.from copies the updated balance; the service proxy commits
   before the controller returns the default 200 response.

The request quantity is an add amount, so 10 + 5 returns 15. No generic setter,
new Entity construction or save/insert is used by this path. Employee identity
is not persisted because the shared contract defines no Employee audit data.

## Why the row is locked

Without a lock, A and B could both read 10; A adds 5 and writes 15, B adds 7
and writes 17, losing A's update. With the row lock, A reads 10 and commits 15;
B then acquires the lock, reads 15 and commits 22. The DB row is the boundary
until transaction commit/rollback, including flush and response projection.
Different item types maintain independent balances.

> “Inventory는 품목별 현재 수량 한 건만 유지합니다. POST는 새 row를 만드는 기능이
> 아니라 기존 aggregate row에 수량을 더하는 기능입니다. 같은 품목 row 자체가
> lock boundary라서 동시에 들어온 add 요청도 순서대로 현재 값을 읽고 더합니다.”

No JVM/global mutex or retry loop is introduced. The lock read is the first DB
operation in the command transaction and reads the target's current state; it
needs no preceding consistent-read aggregate snapshot. Default transaction
isolation is retained, rather than copying B5's READ_COMMITTED aggregate strategy.

## Validation and internal failure

NotNull itemType / quantity report 422 VALIDATION_FAILED with REQUIRED.
Positive Long quantity rejects zero and negative adds with OUT_OF_RANGE.
Existing strict Jackson settings reject unknown enums/properties, fractional
quantities, numeric strings and values outside Java long with 400 MALFORMED_REQUEST.
No new public maximum or field/error code is introduced.

Physical quantity is Java long / SQL BIGINT. A valid positive add to
Long.MAX_VALUE - 2 by 5 fails Math.addExact before assignment, returns safe
500 INTERNAL_ERROR through the existing generic handler and rolls back.
The prior balance remains intact; exact Long.MAX_VALUE is representable.
Missing catalog rows use the same safe internal error. Public responses contain
no exception class, SQL, quantity internals or exception message. Existing safe
logging is unchanged. Anonymous callers receive 401 AUTHENTICATION_REQUIRED;
CUSTOMER callers receive 403 FORBIDDEN through the actual security filter.

## Verification and limits

InventoryApiIntegrationTests uses real SecurityFilterChain/JWT, Flyway V1/V2/V3
and Hibernate validation in isolated H2 MySQL mode. Fixtures reset quantities
before/after tests without reseeding; the missing-row scenario rolls back its
deletion, preserving the original V2 identity. Coverage includes exact fields,
fixed catalog, id ordering, zero stock, add/accumulation, item isolation, long
range, malformed/validation/auth failures, safe overflow rollback and missing row.

InventoryConcurrencyTests starts two real HTTP POSTs together, expects two 200
responses and exact final 5 + 7 = 12. It accepts either serialized response order.
A held-lock test flushes +5 while holding the first transaction, proves the +7
command cannot complete before release/commit, then checks 12. Different-item
concurrent POSTs retain their own quantities. Latches/futures have bounded waits
and workers are released/shut down in finally blocks.

The 325-test B6 baseline is preserved. B7 adds 32 tests (29 API/validation/error
cases and 3 concurrency scenarios), for 357 total with zero failures, errors or
skipped tests. Maven Wrapper test and package both pass; package builds the
executable JAR. git diff --check passes. CI and actual MySQL remain separate
validation boundaries.

Actual MySQL 8 InnoDB SELECT FOR UPDATE, lock waiting, row-level isolation,
deadlock/timeout behavior and EXPLAIN of the unique item_type lookup remain B9
hardening. H2 success does not establish those engine properties. No Docker,
Testcontainers, dependency or index migration is introduced here.

## Scope and next gates

V1/V2/V3 are unchanged and Flyway remains version 3. There is no decrement,
replacement, negative adjustment, PUT/PATCH/DELETE, SKU, stock reservation,
audit endpoint or transaction ledger. Reservation/confirmation never deducts
Inventory, and zero stock never hides a Product or blocks a Reservation.
Frontend, ai-console and shared docs remain unchanged. No shared blocker was found.
Next gates: B8 SMS durable after-commit delivery and B9 integration/hardening.

## B9-A1 runtime validation

The separate actual-MySQL harness, scenario tests and current validation record
are documented in [B9-A1 MySQL runtime hardening](b9-mysql-runtime-hardening.md).
This verifies the current approved v0.2 snapshot; it is not a final freeze.
Real SOLAPI/handset smoke remains B9-A2 and cross-repository E2E remains B9-B/C/D.
