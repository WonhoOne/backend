# Backend Bootstrap

## Status

Backend Internal Approved Decision

## Scope

This document records Bootstrap technology decisions used inside the Backend implementation. It does not define shared API, Business Rule, or Shared Domain contracts.

## Approved Decisions

| Item | Value |
| --- | --- |
| Java | 21 |
| Build Tool | Maven |
| Spring Boot | 4.1.1 |
| Base Package | `com.wonhoone.misterworld` |

## Initial Capability Boundary

The initial project prepares only these capabilities:

- Web / REST
- Bean Validation
- JPA persistence
- MySQL connectivity
- Backend testing

## Explicitly Deferred

The following decisions remain open:

- Authentication method
- JWT versus session
- Spring Security
- Customer / Employee persistence structure
- User + Role structure
- Request DTO and Response DTO
- Error response format
- Pagination
- Authentication token format
- Reservation status
- Cancellation
- Separate TravelHistory table
- Price calculation
- Detailed Loyalty rules
- SMS provider
- Inventory deduction timing
- Detailed Hotel / Transport / Meal models
- Detailed package architecture
- Deployment
- CI/CD

## Documentation Boundary

Shared contracts belong in `WonhoOne/docs`. Backend-internal implementation documentation belongs in `WonhoOne/backend/docs`. If a Backend-internal decision begins to affect another repository's contract, propose a change to the shared docs first and wait for approval before implementation. Do not duplicate shared contracts locally or resolve shared TBD items in Backend-local documentation.
