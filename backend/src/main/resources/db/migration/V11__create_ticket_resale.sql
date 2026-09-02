CREATE TABLE event_resale_policy (
    event_id UUID PRIMARY KEY REFERENCES event(id),
    organization_id UUID NOT NULL REFERENCES organization(id),
    enabled BOOLEAN NOT NULL,
    minimum_price_minor BIGINT NULL,
    maximum_price_minor BIGINT NULL,
    listing_cutoff_minutes INTEGER NOT NULL,
    resale_fee_minor BIGINT NULL,
    resale_fee_basis_points INTEGER NULL,
    checked_in_ineligible BOOLEAN NOT NULL,
    refunded_ineligible BOOLEAN NOT NULL,
    revoked_ineligible BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT event_resale_policy_minimum_price_check CHECK (minimum_price_minor IS NULL OR minimum_price_minor >= 0),
    CONSTRAINT event_resale_policy_maximum_price_check CHECK (maximum_price_minor IS NULL OR maximum_price_minor >= 0),
    CONSTRAINT event_resale_policy_price_range_check CHECK (
        maximum_price_minor IS NULL OR minimum_price_minor IS NULL OR maximum_price_minor >= minimum_price_minor
    ),
    CONSTRAINT event_resale_policy_cutoff_check CHECK (listing_cutoff_minutes >= 0),
    CONSTRAINT event_resale_policy_fee_minor_check CHECK (resale_fee_minor IS NULL OR resale_fee_minor >= 0),
    CONSTRAINT event_resale_policy_fee_basis_points_check CHECK (
        resale_fee_basis_points IS NULL OR resale_fee_basis_points BETWEEN 0 AND 10000
    ),
    CONSTRAINT event_resale_policy_fee_xor_check CHECK (
        (resale_fee_minor IS NULL) <> (resale_fee_basis_points IS NULL)
    )
);

CREATE TABLE resale_listing (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    event_id UUID NOT NULL REFERENCES event(id),
    source_entitlement_id UUID NOT NULL REFERENCES ticket_entitlement(id),
    source_credential_id UUID NULL REFERENCES ticket_credential(id),
    seller_email VARCHAR(320) NOT NULL,
    buyer_email VARCHAR(320) NULL,
    currency CHAR(3) NOT NULL,
    listed_price_minor BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_pending_reference VARCHAR(255) NULL,
    cancelled_at TIMESTAMPTZ NULL,
    sold_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT resale_listing_status_check CHECK (status IN ('LISTED', 'PURCHASE_PENDING', 'SOLD', 'CANCELLED')),
    CONSTRAINT resale_listing_price_check CHECK (listed_price_minor >= 0),
    CONSTRAINT resale_listing_currency_check CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX resale_listing_active_source_unique
    ON resale_listing (source_entitlement_id)
    WHERE status IN ('LISTED', 'PURCHASE_PENDING');

CREATE INDEX resale_listing_event_status_index ON resale_listing (event_id, status);

CREATE TABLE resale_transaction (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES resale_listing(id),
    provider_type VARCHAR(32) NOT NULL,
    provider_payment_reference VARCHAR(255) NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT resale_transaction_provider_type_check CHECK (provider_type IN ('MANUAL_VERIFIED')),
    CONSTRAINT resale_transaction_status_check CHECK (status IN ('PAYMENT_PENDING', 'PAYMENT_FAILED', 'PAYMENT_CONFIRMED')),
    CONSTRAINT resale_transaction_listing_key_unique UNIQUE (listing_id, idempotency_key),
    CONSTRAINT resale_transaction_provider_reference_unique UNIQUE (provider_type, provider_payment_reference)
);

CREATE TABLE seller_payout (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL UNIQUE REFERENCES resale_listing(id),
    resale_transaction_id UUID NOT NULL UNIQUE REFERENCES resale_transaction(id),
    seller_email VARCHAR(320) NOT NULL,
    currency CHAR(3) NOT NULL,
    gross_amount_minor BIGINT NOT NULL,
    fee_amount_minor BIGINT NOT NULL,
    net_amount_minor BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT seller_payout_state_check CHECK (state IN ('PENDING', 'PROCESSING', 'PAID', 'FAILED')),
    CONSTRAINT seller_payout_gross_check CHECK (gross_amount_minor >= 0),
    CONSTRAINT seller_payout_fee_check CHECK (fee_amount_minor >= 0),
    CONSTRAINT seller_payout_net_check CHECK (net_amount_minor >= 0),
    CONSTRAINT seller_payout_currency_check CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE resale_audit_event (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES resale_listing(id),
    event_id UUID NOT NULL REFERENCES event(id),
    event_type VARCHAR(64) NOT NULL,
    actor_email VARCHAR(320) NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT resale_audit_event_type_check CHECK (
        event_type IN ('LISTED', 'CANCELLED', 'PURCHASE_PENDING', 'PAYMENT_FAILED', 'SOLD')
    )
);

CREATE INDEX resale_audit_event_listing_index
    ON resale_audit_event (listing_id, created_at);

ALTER TABLE ticket_entitlement
    ADD COLUMN transferred_to_entitlement_id UUID NULL REFERENCES ticket_entitlement(id),
    ADD COLUMN transferred_at TIMESTAMPTZ NULL;
