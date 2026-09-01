-- Orders are created from active reservations. Payment confirmation and ticket issuance are separate later transitions.
CREATE TABLE customer_order (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    event_id UUID NOT NULL REFERENCES event(id),
    reservation_id UUID NOT NULL REFERENCES inventory_reservation(id),
    customer_email VARCHAR(320) NOT NULL,
    currency CHAR(3) NOT NULL,
    total_amount_minor BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_order_status_check CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'PAYMENT_FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT customer_order_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT customer_order_total_nonnegative CHECK (total_amount_minor >= 0),
    CONSTRAINT customer_order_reservation_unique UNIQUE (reservation_id)
);

CREATE TABLE order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(id),
    ticket_type_id UUID NOT NULL REFERENCES ticket_type(id),
    ticket_type_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    line_total_minor BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT order_item_quantity_check CHECK (quantity > 0),
    CONSTRAINT order_item_unit_price_nonnegative CHECK (unit_price_minor >= 0),
    CONSTRAINT order_item_line_total_nonnegative CHECK (line_total_minor >= 0)
);

CREATE TABLE payment_attempt (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(id),
    provider_type VARCHAR(32) NOT NULL,
    provider_payment_reference VARCHAR(255) NULL,
    provider_checkout_reference VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payment_attempt_provider_type_check CHECK (provider_type IN ('STRIPE')),
    CONSTRAINT payment_attempt_status_check CHECK (status IN ('CREATED', 'CHECKOUT_STARTED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT payment_attempt_idempotency_unique UNIQUE (order_id, idempotency_key),
    CONSTRAINT payment_attempt_checkout_reference_unique UNIQUE (provider_type, provider_checkout_reference)
);

CREATE INDEX customer_order_event_id_index ON customer_order (event_id);
CREATE INDEX payment_attempt_order_id_index ON payment_attempt (order_id);
