ALTER TABLE notification_outbox
    DROP CONSTRAINT notification_outbox_type_check;

ALTER TABLE notification_outbox
    ADD COLUMN subject_reference VARCHAR(64) NULL,
    ADD COLUMN magic_link_token_hash CHAR(64) NULL,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN last_attempt_at TIMESTAMPTZ NULL,
    ADD COLUMN claimed_at TIMESTAMPTZ NULL,
    ADD COLUMN claim_token_hash CHAR(64) NULL,
    ADD COLUMN delivered_at TIMESTAMPTZ NULL,
    ADD COLUMN failed_at TIMESTAMPTZ NULL,
    ADD COLUMN last_error TEXT NULL,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN max_attempts INTEGER NOT NULL DEFAULT 5;

UPDATE notification_outbox
SET status = CASE WHEN published_at IS NULL THEN 'PENDING' ELSE 'DELIVERED' END,
    next_attempt_at = COALESCE(next_attempt_at, created_at),
    delivered_at = COALESCE(delivered_at, published_at),
    updated_at = COALESCE(updated_at, published_at, created_at);

ALTER TABLE notification_outbox
    ADD CONSTRAINT notification_outbox_type_check CHECK (notification_type IN ('CUSTOMER_TICKET_WALLET_MAGIC_LINK', 'PURCHASE_CONFIRMATION')),
    ADD CONSTRAINT notification_outbox_status_check CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DELIVERED', 'FAILED')),
    ADD CONSTRAINT notification_outbox_magic_link_hash_check CHECK (notification_type <> 'CUSTOMER_TICKET_WALLET_MAGIC_LINK' OR magic_link_token_hash IS NOT NULL),
    ADD CONSTRAINT notification_outbox_max_attempts_check CHECK (max_attempts > 0),
    ADD CONSTRAINT notification_outbox_attempt_window_check CHECK (attempts <= max_attempts);

DROP INDEX IF EXISTS notification_outbox_pending_index;

CREATE INDEX notification_outbox_dispatch_index
    ON notification_outbox (status, next_attempt_at, created_at)
    WHERE status = 'PENDING';
