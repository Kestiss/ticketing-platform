# Ticketing Platform

An enterprise-oriented event ticketing platform for EU/EEA organizers. The product will support primary ticket sales, platform-managed ticket credentials, official ticket resale through revocation and reissuance, organizer teams, and per-event payment-provider selection.

## Current milestone

The first vertical slice delivers this workflow:

1. An organizer creates an event and general-admission ticket type.
2. The event selects one Stripe-backed payment profile.
3. Sales open and lock that event payment profile.
4. A customer reserves inventory and pays.
5. A verified payment webhook confirms the order.
6. The platform issues the customer's ticket entitlement and credential.

The initial commit establishes the local runtime baseline only. Business modules, Stripe integration, Keycloak realm configuration, and the end-to-end flow follow in focused commits.

## Technology baseline

- Java 25 LTS
- Kotlin 2.4.x
- Spring Boot 4.1.x
- PostgreSQL 18.x
- Keycloak 26.7.x
- Docker Compose
- Gradle Kotlin DSL

Only stable production releases are permitted. Preview, milestone, beta, release-candidate, snapshot, and `latest` container versions are not accepted.

## Local prerequisites

- JDK 25
- Docker Engine with Docker Compose

## Start local infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL on port `5432` and Keycloak on port `8081`. The credentials in `compose.yaml` are deliberately local-development-only values.

## Run the API

```bash
./gradlew :backend:bootRun --args='--spring.profiles.active=local'
```

The unauthenticated health endpoint is available at:

```text
GET http://localhost:8080/api/v1/system/health
```

## Repository layout

```text
backend/                Kotlin and Spring Boot modular monolith
compose.yaml            Local PostgreSQL and Keycloak services
docs/                   Architecture decisions, threat models, and runbooks
```

## Security notes

- Never commit real credentials, private keys, payment secrets, or production data.
- Customer ticket access will use short-lived, single-use magic links.
- Personnel accounts will be invitation-only and use password authentication with MFA support.
- Raw card data will never pass through this application.
- Payment-provider choice is locked per event once sales begin.

## License

Licensed under the [Apache License 2.0](LICENSE).
