-- Organization foundation.
CREATE TABLE organization (
    id UUID PRIMARY KEY,
    legal_name VARCHAR(200) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    default_locale VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT organization_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT organization_legal_name_not_blank CHECK (btrim(legal_name) <> ''),
    CONSTRAINT organization_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE UNIQUE INDEX organization_legal_name_unique
    ON organization (lower(legal_name));
