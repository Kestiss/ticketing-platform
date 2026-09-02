## Online gate validation

The first scanner flow is online-only:

1. An organizer creates a scanner device for one event and receives a one-time device secret.
2. The scanner sends its device ID, secret, event ID, and a customer presentation claim to the validation endpoint.
3. The server validates the HMAC claim, scanner assignment, event identity, ticket-entitlement state, and credential state.
4. A unique admitted record prevents the same entitlement from being admitted twice, even if two online scanners submit concurrently.
5. Every acceptance and rejection becomes an immutable admission record.

Offline validation is deliberately deferred. It needs versioned public signing keys, bounded revocation snapshots, synchronization, device expiry, and an explicit security policy for resale near event entry.
