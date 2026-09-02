CREATE TABLE scanner_device (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    display_name VARCHAR(120) NOT NULL,
    device_secret_hash CHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NULL,
    CONSTRAINT scanner_device_status_check CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT scanner_device_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE TABLE scanner_assignment (
    scanner_device_id UUID NOT NULL REFERENCES scanner_device(id),
    event_id UUID NOT NULL REFERENCES event(id),
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (scanner_device_id, event_id)
);

CREATE TABLE admission_record (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    event_id UUID NOT NULL REFERENCES event(id),
    ticket_entitlement_id UUID NULL REFERENCES ticket_entitlement(id),
    scanner_device_id UUID NOT NULL REFERENCES scanner_device(id),
    credential_version INTEGER NULL,
    outcome VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    scanned_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT admission_record_outcome_check CHECK (outcome IN ('ADMITTED', 'REJECTED'))
);

CREATE INDEX admission_record_event_scanned_index ON admission_record (event_id, scanned_at);
CREATE UNIQUE INDEX admission_record_one_admission_per_entitlement
    ON admission_record (ticket_entitlement_id)
    WHERE outcome = 'ADMITTED';
