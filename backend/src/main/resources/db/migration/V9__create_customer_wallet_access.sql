CREATE TABLE customer_magic_link (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(32) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    request_ip_hash CHAR(64) NULL,
    CONSTRAINT customer_magic_link_purpose_check CHECK (purpose IN ('TICKET_WALLET')),
    CONSTRAINT customer_magic_link_expiry_check CHECK (expires_at > requested_at)
);

CREATE INDEX customer_magic_link_email_purpose_index
    ON customer_magic_link (email, purpose, expires_at);

CREATE TABLE customer_wallet_session (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    session_token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    CONSTRAINT customer_wallet_session_expiry_check CHECK (expires_at > created_at)
);

CREATE INDEX customer_wallet_session_email_index
    ON customer_wallet_session (email, expires_at);
