# Ticketing Platform

An enterprise-oriented event ticketing platform for EU/EEA organizers. It will support primary sales, platform-managed ticket credentials, official resale through revocation and reissuance, organizer teams, and a payment provider chosen and locked per event.

## Current vertical-slice status

Implemented locally:

- Organization creation and read API
- Organizer-owned Stripe payment-profile metadata, without Stripe secrets or live API calls
- Event and general-admission ticket-type creation
- Validation that payment profiles belong to the event organizer
- Sales opening only when an event has an active ticket type and an active payment profile
- Atomic lock of an event payment profile when sales open

Next: inventory reservations, then order creation and Stripe Checkout.

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

## Run the API

```bash
./gradlew :backend:bootRun --args='--spring.profiles.active=local'
```

## Example API flow

Create an organizer:

```bash
curl -X POST http://localhost:8080/api/v1/organizations \
  -H 'Content-Type: application/json' \
  -d '{"legalName":"Example Events GmbH","displayName":"Example Events","defaultLocale":"en-GB"}'
```

Create a Stripe payment profile, using the returned organization ID:

```bash
curl -X POST http://localhost:8080/api/v1/organizations/{organizationId}/payment-profiles \
  -H 'Content-Type: application/json' \
  -d '{"providerAccountReference":"acct_example","settlementCurrency":"EUR"}'
```

Create an event and ticket type, then open sales with the selected payment-profile ID. Once sales open, the profile is immutable for that event.

## Security notes

- Never commit real credentials, private keys, payment secrets, or production data.
- Customer ticket access will use short-lived, single-use magic links.
- Personnel accounts will be invitation-only and use password authentication with MFA support.
- Raw card data will never pass through this application.
- Bootstrap endpoints are temporarily open for local development and will be replaced with Keycloak authorization before a deployable environment.

## License

Licensed under the [Apache License 2.0](LICENSE).
