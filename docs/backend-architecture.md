# Backend Architecture

## Contract and implementation boundary

Shared reference: WonhoOne/docs main Baseline v0.2 at
`cad8daed210cfb60078f24cabe14c2f383f3ef65`.
This document describes Backend-local organization only.

## Package responsibilities

Under `com.wonhoone.misterworld`:

| Package | Responsibility |
| --- | --- |
| domain | Pure Java business rules and approved value catalogs |
| application | Use-case coordination; existing in-memory reservation example |
| application.port | External capability boundary: SmsSender |
| application.auth | Signup/login, credential normalization and Employee bootstrap |
| application.tour | Product query/command coordination, write validation and DTO projections |
| application.reservation | Loyalty eligibility foundation; final create orchestration remains B5 |
| application.time / config | Shared business-date provider and configurable business Clock |
| api.controller / api.dto | HTTP mappings, validated requests and safe response projections |
| api.error | D-10 error bodies, validation and exception mapping |
| security | Visible endpoint boundaries, JWT issue/validation and principal mapping |
| infrastructure.persistence.entity | JPA identity, columns, and database relationships |
| infrastructure.persistence.repository | Direct Spring Data access to persisted records |

B2 adds API/security packages with concrete authentication implementations.
B3 implements product/schedule flows with separate query/command services,
pure reservability policy and recruitment projection. See
[tour-catalog-schedule-api.md](tour-catalog-schedule-api.md) for the walkthrough.
B4 adds pure Reservation rules, snapshot persistence and Loyalty eligibility, and
enforces BR-31 in Employee Product PUT. See
[reservation-domain-persistence.md](reservation-domain-persistence.md).
infrastructure.sms remains deferred.

## Domain object and JPA entity

Domain object != JPA Entity. Pure models delegate party validation to B4's policy; new persistence
classes carry the JpaEntity suffix and depend on domain enums, not vice versa.
Business-rule presentation stays free of ORM lifecycle details, and mapping
changes do not force a rewrite of the pure model.

There is no speculative port/adapter layer around every Spring Data repository.
Introduce a boundary when an actual application use case needs it. B1 repositories
are not yet connected to the legacy reservation service; no automatic domain/entity
mapping is implied.

## Expected request flow

Auth flow is implemented as described in [auth-security.md](auth-security.md).
Implemented catalog/schedule flow: Controller → Application Service → Domain Rule →
Persistence Repository → DB. Application services will own transaction boundaries
and explicitly construct/provide the data needed by domain rules. Controllers
will handle DTO and HTTP concerns. Exact new class names are intentionally left
to the implementation of each use case.

## Presentation / maintainability guideline

Use names that expose business intent and keep methods focused on one responsibility.
Avoid clever abstraction and implicit code generation; no Lombok is introduced.
Use rationale comments or Javadoc where a design choice is otherwise hard to
explain, rather than translating each line into prose. Tests describe business
scenarios. Confirmation, price snapshots, ownership, concurrency and after-commit
SMS must be traceable through named steps as their use cases are implemented.
