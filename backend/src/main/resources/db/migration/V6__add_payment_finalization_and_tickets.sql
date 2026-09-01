ALTER TABLE customer_order
    ADD COLUMN payment_profile_id UUID NULL REFERENCES payment_profile(id);

UPDATE customer_order orders
SET payment_profile_id = event.payment_profile_id
FROM event
WHERE event.id = orders.event_id;

ALTER TABLE customer_order
    ALTER COLUMN payment_profile_id SET NOT NULL;

CREATE TABLE processed_provider_event (
    provider_type VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (provider_type, provider_event_id),
    CONSTRAINT processed_provider_event_provider_check CHECK (provider_type IN ('STRIPE'))
);

CREATE TABLE ticket_entitlement (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    event_id UUID NOT NULL REFERENCES event(id),
    order_id UUID NOT NULL REFERENCES customer_order(id),
    ticket_type_id UUID NOT NULL REFERENCES ticket_type(id),
    owner_email VARCHAR(320) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ticket_entitlement_status_check CHECK (status IN ('ACTIVE', 'REVOKED', 'REFUNDED', 'CHECKED_IN'))
);

CREATE TABLE ticket_credential (
    id UUID PRIMARY KEY,
    ticket_entitlement_id UUID NOT NULL REFERENCES ticket_entitlement(id),
    version INTEGER NOT NULL,
    credential_token_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    CONSTRAINT ticket_credential_status_check CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ticket_credential_version_unique UNIQUE (ticket_entitlement_id, version),
    CONSTRAINT ticket_credential_active_unique UNIQUE NULLS NOT DISTINCT (ticket_entitlement_id, status)
);

CREATE INDEX ticket_entitlement_order_id_index ON ticket_entitlement (order_id);
