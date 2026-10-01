# Persistence Foundation

## Scope and source

B1 provides UserAccount, TourProduct, style-price, TourSchedule, and Inventory
storage. Shared meanings follow WonhoOne/docs main v0.2, snapshot
`cad8daed210cfb60078f24cabe14c2f383f3ef65`.
The shared ERD is logical; it is not a one-to-one physical schema prescription.
B4 adds Reservation configuration/price and historical Product/date snapshots in
V3; see [reservation-domain-persistence.md](reservation-domain-persistence.md).
B6 History is a scalar projection over these records, with no new table or migration;
see [travel-history.md](travel-history.md).
B5 connects Reservation REST and persisted Schedule aggregates using a target-row
pessimistic lock and READ_COMMITTED create transaction. No migration is added;
see [reservation-api-concurrency.md](reservation-api-concurrency.md).

## Schema ownership and migration policy

Spring Boot's Flyway starter supplies Flyway core and its Boot 4 auto-configuration;
flyway-mysql supplies MySQL support. Versions come from Boot 4.1.1 dependency
management. No separate Flyway version is pinned.

Flyway is the only schema writer. Hibernate uses ddl-auto=validate and
open-in-view=false. V1 creates core tables; V2 initializes the fixed Inventory
catalog. Once applied/shared, migrations are immutable: append a versioned
migration for subsequent changes, never use Hibernate update or rewrite history.

## Physical decisions

| Table | Constraints and meaning |
| --- | --- |
| user_account | BIGINT identity PK; unique login_id; required profile, password_hash and role |
| tour_product | BIGINT identity PK; required Theme, name and description |
| tour_product_style_price | BIGINT identity PK; FK to product; unique (tour_product_id, style); amount > 0 |
| tour_schedule | BIGINT identity PK; FK to product; required dates with start_date <= end_date; confirmed defaults false |
| inventory | BIGINT identity PK; unique item_type; quantity >= 0 |

All enum columns use VARCHAR(32), EnumType.STRING and explicit VARCHAR JDBC mapping.
This keeps MySQL/H2 on the same portable string representation instead of relying
on Hibernate's dialect-specific native ENUM default. Checks restrict canonical
values. Monetary amount and stock quantity use Java long / SQL BIGINT: whole KRW
and whole units without floating-point rounding. Identity uses nullable Long
until the database assigns the ID.

String storage sizes are Backend-local: login_id, password_hash, name and contact
255; address 500; description 2000. These are storage bounds, not new public DTO
rules. B2/B3 must coordinate request validation/storage limits before API writes.
B2 canonicalizes login IDs with trim + Locale.ROOT lowercase before storage/lookup.
Existing accounts receive a transactional startup compatibility pass; canonical
collisions fail startup. See [auth-security.md](auth-security.md).

Currency is not stored per style-price row because v0.2 has only KRW; a later API
projection must still return currency=KRW. Theme/Style eligibility and completeness
of a product's price set remain Domain/Application responsibilities.

Relationships are unidirectional lazy ManyToOne from price/schedule to product,
without cascade delete or inverse collections. Each repository is a direct
Spring Data JpaRepository. Constructors accept required storage values with basic
null/range checks; no public setters are added. B7 adds the intent-specific
InventoryJpaEntity.addQuantity method with positive-amount and overflow checks.
UserAccount receives an already encoded passwordHash; B1 does not encode passwords.

No reservable or recruitment-count columns are stored. B4 adds Reservation tables
in V3 without changing V1/V2. No JWT table, History table, SMS outbox or demo
users/products/schedules are introduced.

## Inventory initialization

V2 creates exactly one zero-stock row per canonical item type. This is the fixed
catalog, not demo data. B7 Employee adds update the existing row under
PESSIMISTIC_WRITE inside InventoryCommandService's write transaction. Managed
Entity dirty checking plus explicit flush updates the aggregate without save/insert.
Math.addExact rejects overflow before assignment; failure rolls back with safe
INTERNAL_ERROR. A missing catalog row fails internally without a replacement.
GET orders rows by id ASC in the repository and includes zero stock.
V1/V2/V3 remain unchanged; Flyway stays at version 3. See
[inventory-api.md](inventory-api.md). B8 SMS and B9 MySQL hardening remain.

## Configuration and tests

Default configuration targets MySQL using DB_URL, DB_USERNAME and DB_PASSWORD.
Create the database/user externally and set those variables before running locally;
no real credentials are tracked. Flyway creates tables inside the existing database.
MySQL 8.0.16 or later is required for enforced CHECK constraints.

The test profile overrides every datasource credential/URL and driver with H2
in-memory MySQL mode. Tests execute the same V1/V2/V3 scripts and Hibernate validate,
not Hibernate create or H2-only substitute migrations. Persistence tests run in
rolled-back transactions; pure domain tests remain Spring-free.

H2 verifies migration execution, mapping, FK/unique/check semantics and the catalog.
It does not prove MySQL collation, locking or engine behavior. Real MySQL integration
validation remains a later integration/hardening gate; no MySQL E2E claim is made.

Boot-managed H2 2.4.240 triggers Flyway 12.4.0's newer-than-verified H2 warning
(verified through 2.3.232). Migration/validation tests pass; keep dependency
management intact and recheck this warning in integration/hardening.
