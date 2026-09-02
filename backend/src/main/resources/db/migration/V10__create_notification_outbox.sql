CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    notification_type VARCHAR(64) NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT notification_outbox_type_check CHECK (notification_type IN ('CUSTOMER_TICKET_WALLET_MAGIC_LINK')),
    CONSTRAINT notification_outbox_attempts_check CHECK (attempts >= 0)
);

CREATE INDEX notification_outbox_pending_index
    ON notification_outbox (created_at)
    WHERE published_at IS NULL;
