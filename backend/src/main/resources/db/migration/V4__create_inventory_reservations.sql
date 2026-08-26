-- General-admission inventory is maintained independently from orders so availability checks are bounded and atomic.
CREATE TABLE ticket_inventory (
    ticket_type_id UUID PRIMARY KEY REFERENCES ticket_type(id),
    sold_quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ticket_inventory_sold_nonnegative CHECK (sold_quantity >= 0),
    CONSTRAINT ticket_inventory_reserved_nonnegative CHECK (reserved_quantity >= 0)
);

INSERT INTO ticket_inventory (ticket_type_id, sold_quantity, reserved_quantity, updated_at)
SELECT id, 0, 0, CURRENT_TIMESTAMP
FROM ticket_type;

CREATE TABLE inventory_reservation (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    event_id UUID NOT NULL REFERENCES event(id),
    ticket_type_id UUID NOT NULL REFERENCES ticket_type(id),
    requested_quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT inventory_reservation_quantity_check CHECK (requested_quantity BETWEEN 1 AND 10),
    CONSTRAINT inventory_reservation_status_check CHECK (status IN ('ACTIVE', 'EXPIRED', 'CONVERTED', 'CANCELLED')),
    CONSTRAINT inventory_reservation_idempotency_key_not_blank CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT inventory_reservation_idempotency_unique UNIQUE (organization_id, event_id, ticket_type_id, idempotency_key)
);

CREATE INDEX inventory_reservation_event_id_index ON inventory_reservation (event_id);
CREATE INDEX inventory_reservation_expiry_index ON inventory_reservation (status, expires_at);
