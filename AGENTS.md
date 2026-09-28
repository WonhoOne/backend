# Backend Agent Instructions

This repository implements the Mister World **Backend / Database** area.

## Mandatory reading before implementation

Before writing or modifying code, read the latest approved documents in `WonhoOne/docs`:

1. `baseline/BASELINE-v0.1.md`
2. `requirements/requirements.md`
3. `requirements/domain-model.md`
4. `requirements/business-rules.md`
5. `architecture/system-architecture.md`
6. `architecture/repository-responsibilities.md`
7. `api/api-spec-draft.md`
8. `database/erd-draft.md`
9. `CONTRIBUTING.md`
10. `AGENTS.md`

Docs repository: https://github.com/WonhoOne/docs

Do not begin implementation against an unapproved local assumption when the required baseline is not yet available on the approved docs branch.

## Backend responsibilities

- Spring Boot REST API
- Core business-rule validation
- Customer / Employee / Tour / Schedule / Reservation / Inventory / History data handling
- MySQL persistence
- Backend unit and integration tests
- Public API implementation matching the approved docs contract

## Non-negotiable rules

- Backend is the final authority for validating shared business rules.
- Do not silently change Business Rules, public REST API contracts, shared domain terminology, or ERD contracts.
- Do not invent requirements or resolve TBD items by assumption.
- If a shared contract needs to change, propose the docs change first and state the impact on `frontend` and `ai-console`.
- Do not implement Customer GUI or Voice Recognition in this repository.
- Keep internal implementation choices flexible unless they affect a shared contract.
- If code and docs conflict, stop and surface the conflict instead of choosing an interpretation silently.

## PR expectations

Every implementation PR should identify:

- related Requirement IDs
- related Business Rule IDs when applicable
- affected API endpoints / DB entities
- tests executed
- whether any shared contract changed
