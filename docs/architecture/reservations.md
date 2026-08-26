## Reservation processing

Reservations are held for 15 minutes. A scheduled worker claims expired active reservations using PostgreSQL `FOR UPDATE SKIP LOCKED`, marks them expired, and releases capacity within the same transaction. This prevents two workers from releasing a hold twice.

The inventory counter is intentionally separate from orders. A later payment-confirmation transaction will convert `reserved_quantity` into `sold_quantity` while marking the reservation `CONVERTED`.
