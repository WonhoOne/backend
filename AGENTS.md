# Backend Agent Instructions

This repository implements the Mister World **Backend / Database** area.

`WonhoOne/docs` **main** is the approved shared SSOT. Implementation must use the latest Baseline present on `docs/main`; docs feature branches and unmerged docs PRs are proposals.

## Mandatory reading before implementation

Before writing or modifying code, read the latest approved documents in `WonhoOne/docs`:

1. The latest Baseline present on `docs/main`
2. `requirements/requirements.md`
3. `requirements/product-catalog.md`
4. `requirements/domain-model.md`
5. `requirements/business-rules.md`
6. `requirements/non-functional-requirements.md`
7. `architecture/system-architecture.md`
8. `architecture/repository-responsibilities.md`
9. `api/api-spec-draft.md`
10. `database/erd-draft.md`
11. `CONTRIBUTING.md`
12. `AGENTS.md`

Docs repository: https://github.com/WonhoOne/docs

Do not begin implementation against an unapproved local assumption when the required baseline is not available on the approved docs branch.

## Backend responsibilities

- Spring Boot REST API
- Core business-rule validation
- Customer / Employee / Tour / Schedule / Reservation / Inventory / History data handling
- MySQL persistence
- Backend unit and integration tests
- Public API implementation matching the approved docs contract

## Cross-repository access

- `WonhoOne/backend` is this Agent's writable implementation area.
- `WonhoOne/frontend` and `WonhoOne/ai-console` are **read-only by default**.
- Their code may be inspected for API usage, integration debugging, and impact analysis.
- Do not modify, commit to, or open implementation PRs against those repositories unless their Owner or the team explicitly delegates the task.
- If another repository needs a change, create/request an Issue for its Owner with the required behavior, contract impact, and reproduction context.

## Documentation placement policy

- `WonhoOne/docs/main` is the shared contract SSOT. Only content merged there is approved.
- Contracts that other repositories need to implement or integrate against belong in `WonhoOne/docs`.
- Backend-only implementation documentation belongs in `WonhoOne/backend/docs/`.
- If a Backend-internal decision begins to affect a cross-repository contract, propose the change in the shared docs first and wait for approval before implementing it.
- Do not maintain duplicate copies of the same contract in shared docs and Backend-local docs.
- Do not resolve TBD items in Backend-local documentation as though they were approved shared contracts.

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
