# Architecture decisions

Architecture decisions are recorded here before implementation changes that are difficult to reverse.

## Accepted decisions

1. The backend is a Kotlin/Spring Boot modular monolith.
2. PostgreSQL is the transactional system of record.
3. Each event uses one payment profile, locked when sales open.
4. Stripe is the first payment-provider adapter.
5. Customers use magic-link authentication; personnel use invitation-only password authentication with MFA support.
6. Keycloak is self-hosted for identity and authentication.
7. The first scope uses general-admission inventory.
8. Financial and ticket lifecycle changes require idempotency, audit records, and explicit state transitions.
