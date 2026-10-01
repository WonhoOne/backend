# Authentication and Security (B2)

Backend-local implementation decisions for FR-01, BR-16 and BR-12, with scenario
tests supporting NFR-06. Approved shared reference:
[Baseline v0.2](https://github.com/WonhoOne/docs/blob/79955fc9c864ad0efce6dee9db2319e684573e7a/baseline/BASELINE-v0.2.md)
and [Auth / D-10 errors](https://github.com/WonhoOne/docs/blob/79955fc9c864ad0efce6dee9db2319e684573e7a/api/api-spec-draft.md).
Shared Contract changed: **NO**. Wire definitions remain in the SSOT.

## Reading the flow for a presentation

Start at AuthController, then AuthService, JwtTokenService, and SecurityConfig.
These are three short flows that can be explained in 3–5 minutes:

```text
Signup request → DTO validation → normalize loginId → duplicate check
               → BCrypt hash → save CUSTOMER → user projection (201, no token)

Login request  → DTO validation → normalize loginId → account lookup
               → PasswordEncoder.matches → issue JWT → login projection

Protected request → Spring Bearer filter → Nimbus signature / claim validation
                  → Jwt principal → role authority → endpoint authorization
```

The controller owns HTTP mappings. AuthService owns signup/login orchestration
and transaction boundaries. JPA entities never enter public responses. Responses
use explicit DTO projections and cannot accidentally serialize passwordHash.
An unknown account still performs a dummy BCrypt comparison; its response is
identical to a wrong-password response. The unique DB key handles simultaneous
signup attempts after the application pre-check.

## Login ID and existing rows

LoginIdNormalizer applies Java trim() followed by toLowerCase(Locale.ROOT) before
storage and lookup. Credential case semantics therefore do not depend on the
machine locale or a database case-insensitive lookup. DTO validation checks both
raw and canonical lengths because Unicode lowercase can expand a string.

LegacyLoginIdCanonicalizer runs transactionally before Employee provisioning.
It reads existing accounts, computes canonical keys in Java, and checks the
entire plan for blank/oversized keys and collisions before changing rows.
Safe rows retain their identity, role, profile and passwordHash. Canonical
collisions fail startup with a clear message; an operator must reconcile those
accounts before restarting. No account is deleted or arbitrarily selected.
This compatibility pass is idempotent and scans accounts at startup; revisit
its cost and coordinate startup instances when deploying at larger scale.
External direct DB writes must respect the same canonicalization.

V1 and V2 remain unchanged. No new schema migration is needed. MySQL's other
collation equivalences and concurrent multi-instance startup still need real
MySQL integration validation; H2 is not evidence for those engine details.

## Password handling and input validation

Spring Security BCryptPasswordEncoder uses its standard default strength.
Signup and provisioning call encode; login calls matches. Raw passwords are
never persisted, returned, or logged. Credential DTOs override generated
toString, as do token-bearing responses. Error bodies use fixed safe messages,
never rejected values, SQL errors, or exception messages.

Required strings use NotBlank; loginId/name/contact are bounded at 255 Java
characters and address at 500, matching existing storage. Password input is
bounded to 72 UTF-8 bytes by ValidPassword. This is a Backend-local BCrypt
storage/algorithm safeguard, not a new shared password-complexity rule.
Multibyte inputs are rejected rather than truncated; no uppercase/digit/symbol
requirement is introduced. Blank/null fields report REQUIRED; length failures
report OUT_OF_RANGE with 422 VALIDATION_FAILED.

The Boot-managed Jackson mapper rejects non-string scalars rather than coercing
them into credentials, and rejects unknown request properties (including role).
Malformed JSON and type/shape failures return 400 MALFORMED_REQUEST.

## Tokens and principal

Boot-managed Spring Security OAuth2 Resource Server / JOSE supplies Nimbus JWT
encoder and decoder; no independently pinned JWT dependency is introduced.
HS256 is the only accepted signature algorithm. The signing key is external
JWT_SECRET and must have at least 32 UTF-8 bytes. Use a randomly generated key
from secret management; the length check is not a measure of entropy.
Default application startup fails clearly if the key is blank/undersized.
The test profile contains only a deterministic test key.

JWT_EXPIRES_IN_SECONDS defaults to 3600 and must be positive. It is Backend-local.
Tokens contain sub (positive account ID), role (CUSTOMER or EMPLOYEE), iat and exp.
There is no loginId or personal profile claim. Login's expiresIn uses seconds.
No refresh, logout, or token persistence is added.

Nimbus verifies HS256 signatures before custom OAuth2TokenValidator checks
required timestamps, positive identity, supported role, expiry and optional nbf.
Timestamp checks use UTC with zero clock skew; synchronize deployment clocks.
Only verified, structurally valid tokens can produce the dedicated expired error.
Stateless tokens remain valid until expiry; role/account changes do not revoke
already issued tokens. Revocation is outside B2.

Spring maps role to ROLE_CUSTOMER / ROLE_EMPLOYEE. JwtAuthenticationToken names
the principal by sub. Future controllers can accept @AuthenticationPrincipal Jwt
and use AuthenticatedUser.from(jwt) to pass account identity into application
services without a static SecurityContext helper or client-supplied owner ID.

## Authorization and common errors

SecurityConfig explicitly shows approved public GET reads, public auth POSTs,
CUSTOMER Reservation/History paths and EMPLOYEE paths. Other paths/methods are
denied. These rules prepare later APIs without implementing their controllers.
Sessions are STATELESS, request caching is disabled, and CSRF is disabled because
authentication uses an explicit Bearer header rather than browser cookies.
No frontend origin or CORS policy is invented in B2.

SecurityErrorHandlers uses the same ApiError body as ApiExceptionHandler:

| Situation | HTTP / code |
| --- | --- |
| No Authorization header on a protected path | 401 AUTHENTICATION_REQUIRED |
| Malformed header, token, signature or claims | 401 INVALID_ACCESS_TOKEN |
| Verified token with expired exp | 401 ACCESS_TOKEN_EXPIRED |
| Invalid login credentials | 401 LOGIN_FAILED |
| Authenticated caller without permission | 403 FORBIDDEN |
| Duplicate canonical login ID | 409 LOGIN_ID_ALREADY_EXISTS |
| DTO validation failure | 422 VALIDATION_FAILED |
| Malformed JSON / request type | 400 MALFORMED_REQUEST |
| Invalid path/query parsing | 400 INVALID_QUERY_PARAMETER |
| Unexpected application failure | 500 INTERNAL_ERROR |

fieldErrors is always an array. FieldErrorCode declares the shared vocabulary;
unused codes are available for subsequent use cases. Clients branch on stable
codes, never human messages. 401 responses also include WWW-Authenticate: Bearer.

Expiry distinction uses standard typed Spring APIs: InvalidBearerTokenException
with JwtValidationException and its OAuth2Error list. There is no message parsing,
reflection, or decoding of an unverified token to determine expiry.
Integration tests exercise the actual filter and exception chain.

## Employee provisioning and local startup

Provide DB_URL, DB_USERNAME, DB_PASSWORD and JWT_SECRET via the environment or
deployment secret configuration before running the default application.
There are no real credentials or default production signing keys in this repo.

Employee bootstrap is disabled by default (EMPLOYEE_BOOTSTRAP_ENABLED=false).
To provision a demo Employee, enable it and supply all of:

- EMPLOYEE_LOGIN_ID
- EMPLOYEE_PASSWORD
- EMPLOYEE_NAME
- EMPLOYEE_ADDRESS
- EMPLOYEE_CONTACT

EmployeeBootstrap validates these values with the same profile/password bounds,
canonicalizes the ID, and creates an encoded EMPLOYEE account only if absent.
Invalid enabled configuration fails startup with variable names, without values.
An existing ID is left unchanged, including an existing CUSTOMER; bootstrap does
not promote accounts, rotate passwords or overwrite profile data. Use a dedicated
Employee ID and disable bootstrap after provisioning. No public Employee signup
or extra endpoint exists.

## Verification and scope

AuthIntegrationTests uses MockMvc with the real Spring Security filter chain,
H2/Flyway and BCrypt. Test-only controllers expose identities for authorization
assertions; they are never packaged into production. Tests cover signup/login,
case normalization, null/blank/length/type errors, secret redaction, real tokens,
signature/expiry distinction, wrong roles, deny-by-default, and statelessness.
Employee tests cover disabled/enabled bootstrap, idempotence, startup properties,
missing config, preserving existing accounts and legacy canonical collisions.
JWT configuration tests cover missing/short secrets and configured seconds.

TourProduct, TourSchedule, Reservation, Travel History, Inventory and SMS APIs
remain for later gates. Existing domain/persistence foundations remain unchanged.
Next gate is B3, after Control Tower review.
