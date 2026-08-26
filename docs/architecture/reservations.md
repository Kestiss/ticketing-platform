
## Reservation API

After sales are open, reserve a ticket type with a client-generated idempotency key:

```bash
curl -X POST http://localhost:8080/api/v1/organizations/{organizationId}/events/{eventId}/reservations \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 2eb08eef-fb5e-4f6b-9e8e-0b1b5050b2e8' \
  -d '{"ticketTypeId":"{ticketTypeId}","quantity":2}'
```

Reservations last 15 minutes. Capacity is reserved atomically in PostgreSQL and will be converted to sold inventory only after a future verified payment step.
