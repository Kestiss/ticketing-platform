-- Payment profiles are organizer-owned merchant configurations. Secrets stay exclusively with the provider.
CREATE TABLE payment_profile (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    provider_type VARCHAR(32) NOT NULL,
    provider_account_reference VARCHAR(255) NOT NULL,
    settlement_currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payment_profile_provider_type_check CHECK (provider_type IN ('STRIPE')),
    CONSTRAINT payment_profile_status_check CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT payment_profile_account_reference_not_blank CHECK (btrim(provider_account_reference) <> ''),
    CONSTRAINT payment_profile_currency_check CHECK (settlement_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT payment_profile_organization_account_unique UNIQUE (organization_id, provider_type, provider_account_reference)
);

CREATE INDEX payment_profile_organization_id_index ON payment_profile (organization_id);

CREATE TABLE event (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    payment_profile_id UUID NULL REFERENCES payment_profile(id),
    name VARCHAR(200) NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_profile_locked_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT event_status_check CHECK (status IN ('DRAFT', 'SCHEDULED', 'ON_SALE', 'SALES_CLOSED', 'CANCELLED')),
    CONSTRAINT event_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT event_ends_after_starts CHECK (ends_at > starts_at),
    CONSTRAINT event_sales_requires_locked_profile CHECK (
        status NOT IN ('ON_SALE', 'SALES_CLOSED') OR (payment_profile_id IS NOT NULL AND payment_profile_locked_at IS NOT NULL)
    )
);

CREATE INDEX event_organization_id_index ON event (organization_id);

CREATE TABLE ticket_type (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES event(id),
    name VARCHAR(200) NOT NULL,
    currency CHAR(3) NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    capacity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ticket_type_status_check CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ticket_type_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ticket_type_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ticket_type_unit_price_check CHECK (unit_price_minor >= 0),
    CONSTRAINT ticket_type_capacity_check CHECK (capacity > 0)
);

CREATE INDEX ticket_type_event_id_index ON ticket_type (event_id);
