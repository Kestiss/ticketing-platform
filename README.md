# Ticketing Platform

An enterprise-oriented EU/EEA event-ticketing platform with organizer teams, platform-managed ticket credentials, official resale through credential revocation and reissuance, and one immutable payment profile per event once sales open.

## First vertical slice: complete backend flow

The backend now supports the core transactional flow:

1. Create an organizer, Stripe payment profile, event, and general-admission ticket type.
2. Open sales, permanently locking the event to one organizer-owned payment profile.
3. Atomically reserve capacity for 15 minutes using an idempotency key.
4. Create one pending order from the active reservation using server-side price data.
5. Start a Stripe-hosted Checkout Session for the event's connected Stripe account.
6. Verify a Stripe `checkout.session.completed` webhook.
7. Atomically convert reserved inventory to sold inventory, mark the order paid, and issue ticket entitlements and credential hashes.

## Technology baseline

- Java 25 LTS
- Kotlin 2.4.x
- Spring Boot 4.1.x
- PostgreSQL 18.x
- Keycloak 26.7.x
- Stripe Java 33.3.0
- Docker Compose
- Gradle Kotlin DSL

Only stable production releases are permitted. Preview, milestone, beta, release-candidate, snapshot, and `latest` container versions are prohibited.

## Local development

Start infrastructure:

```bash
docker compose up -d
```

Run the ordinary local API:

```bash
./gradlew :backend:bootRun --args='--spring.profiles.active=local'
```

For Stripe test-mode checkout and webhook handling, provide local test-mode values in an uncommitted `.env` file and activate the Stripe profile:

```bash
SPRING_PROFILES_ACTIVE=local,stripe \
STRIPE_SECRET_KEY=sk_test_replace_me \
STRIPE_WEBHOOK_SECRET=whsec_replace_me \
./gradlew :backend:bootRun
```

Never commit Stripe secrets or use live credentials locally.

## Important security boundary

The temporary unauthenticated bootstrap routes are for local vertical-slice development only. Before deployment, replace them with Keycloak resource-server authentication, organization membership authorization, personnel roles, and customer magic-link sessions.

## What remains before production

The first backend flow is implemented, but the product is not production-ready. Mandatory work remains: Keycloak realm and authorization integration; transactional outbox and notifications; customer magic-link ticket wallet; signed or rotating QR ticket presentation; gate scanning; refunds, cancellations, and chargebacks; organizer team invitations and scoped roles; seller onboarding and secure resale; operational dashboards; API contract tests; CI; cloud deployment; data-protection and accessibility hardening; penetration testing; and production runbooks.

## License

Licensed under the [Apache License 2.0](LICENSE).
