CREATE TABLE personnel_identity (
    id UUID PRIMARY KEY,
    keycloak_subject VARCHAR(128) NOT NULL UNIQUE,
    verified_email VARCHAR(320) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT personnel_identity_subject_not_blank CHECK (btrim(keycloak_subject) <> ''),
    CONSTRAINT personnel_identity_email_not_blank CHECK (btrim(verified_email) <> '')
);

CREATE TABLE organization_role (
    role_key VARCHAR(64) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE organization_permission (
    permission_key VARCHAR(64) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE role_permission (
    role_key VARCHAR(64) NOT NULL REFERENCES organization_role(role_key),
    permission_key VARCHAR(64) NOT NULL REFERENCES organization_permission(permission_key),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_key, permission_key)
);

CREATE TABLE organization_membership (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    personnel_identity_id UUID NOT NULL REFERENCES personnel_identity(id),
    role_key VARCHAR(64) NOT NULL REFERENCES organization_role(role_key),
    invited_by_personnel_identity_id UUID NULL REFERENCES personnel_identity(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    revoked_by_personnel_identity_id UUID NULL REFERENCES personnel_identity(id)
);

CREATE UNIQUE INDEX organization_membership_active_unique
    ON organization_membership (organization_id, personnel_identity_id)
    WHERE revoked_at IS NULL;

CREATE INDEX organization_membership_org_index
    ON organization_membership (organization_id)
    WHERE revoked_at IS NULL;

CREATE TABLE membership_event_scope (
    membership_id UUID NOT NULL REFERENCES organization_membership(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES event(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (membership_id, event_id)
);

CREATE INDEX membership_event_scope_event_index ON membership_event_scope (event_id);

CREATE TABLE organization_invitation (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    invited_email VARCHAR(320) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    requested_role_key VARCHAR(64) NOT NULL REFERENCES organization_role(role_key),
    selected_event_id UUID NULL REFERENCES event(id),
    inviter_personnel_identity_id UUID NOT NULL REFERENCES personnel_identity(id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ NULL,
    accepted_by_personnel_identity_id UUID NULL REFERENCES personnel_identity(id),
    revoked_at TIMESTAMPTZ NULL,
    revoked_by_personnel_identity_id UUID NULL REFERENCES personnel_identity(id),
    CONSTRAINT organization_invitation_email_not_blank CHECK (btrim(invited_email) <> ''),
    CONSTRAINT organization_invitation_expires_after_created CHECK (expires_at > created_at),
    CONSTRAINT organization_invitation_mutual_exclusive CHECK (accepted_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX organization_invitation_lookup_index
    ON organization_invitation (organization_id, invited_email, expires_at)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

INSERT INTO organization_role (role_key, description) VALUES
    ('OWNER', 'Organization owner with full authority and transfer rights'),
    ('ORGANIZATION_ADMIN', 'Administrative role for organization operations and team management'),
    ('EVENT_MANAGER', 'Manages event setup, ticket types, and sales operations'),
    ('FINANCE_MANAGER', 'Manages payment profiles and financial reporting access'),
    ('BOX_OFFICE_MANAGER', 'Manages box office and order operations'),
    ('GATE_MANAGER', 'Manages scanner operations and gate teams'),
    ('SCANNER', 'Scans and validates tickets at entry points'),
    ('ANALYST', 'Read-only analytics and reporting access');

INSERT INTO organization_permission (permission_key, description) VALUES
    ('ORGANIZATION_READ', 'Read organization details'),
    ('ORGANIZATION_MANAGE', 'Manage organization settings'),
    ('PAYMENT_PROFILE_MANAGE', 'Create and read payment profiles'),
    ('EVENT_READ', 'Read event configuration and state'),
    ('EVENT_MANAGE', 'Create and update event configuration'),
    ('TICKET_TYPE_MANAGE', 'Create ticket types'),
    ('SALES_OPEN', 'Open event sales with immutable payment profile lock'),
    ('ORDER_READ', 'Read organizer order information'),
    ('REPORT_READ', 'Read organizer operational and financial reports'),
    ('SCANNER_MANAGE', 'Manage scanner and gate operations'),
    ('MEMBERSHIP_INVITE', 'Invite organization members'),
    ('MEMBERSHIP_MANAGE', 'Manage membership role and revocation'),
    ('OWNERSHIP_TRANSFER', 'Transfer organization ownership explicitly');

INSERT INTO role_permission (role_key, permission_key) VALUES
    ('OWNER', 'ORGANIZATION_READ'),
    ('OWNER', 'ORGANIZATION_MANAGE'),
    ('OWNER', 'PAYMENT_PROFILE_MANAGE'),
    ('OWNER', 'EVENT_READ'),
    ('OWNER', 'EVENT_MANAGE'),
    ('OWNER', 'TICKET_TYPE_MANAGE'),
    ('OWNER', 'SALES_OPEN'),
    ('OWNER', 'ORDER_READ'),
    ('OWNER', 'REPORT_READ'),
    ('OWNER', 'SCANNER_MANAGE'),
    ('OWNER', 'MEMBERSHIP_INVITE'),
    ('OWNER', 'MEMBERSHIP_MANAGE'),
    ('OWNER', 'OWNERSHIP_TRANSFER'),
    ('ORGANIZATION_ADMIN', 'ORGANIZATION_READ'),
    ('ORGANIZATION_ADMIN', 'ORGANIZATION_MANAGE'),
    ('ORGANIZATION_ADMIN', 'PAYMENT_PROFILE_MANAGE'),
    ('ORGANIZATION_ADMIN', 'EVENT_READ'),
    ('ORGANIZATION_ADMIN', 'EVENT_MANAGE'),
    ('ORGANIZATION_ADMIN', 'TICKET_TYPE_MANAGE'),
    ('ORGANIZATION_ADMIN', 'SALES_OPEN'),
    ('ORGANIZATION_ADMIN', 'ORDER_READ'),
    ('ORGANIZATION_ADMIN', 'REPORT_READ'),
    ('ORGANIZATION_ADMIN', 'SCANNER_MANAGE'),
    ('ORGANIZATION_ADMIN', 'MEMBERSHIP_INVITE'),
    ('ORGANIZATION_ADMIN', 'MEMBERSHIP_MANAGE'),
    ('EVENT_MANAGER', 'EVENT_READ'),
    ('EVENT_MANAGER', 'EVENT_MANAGE'),
    ('EVENT_MANAGER', 'TICKET_TYPE_MANAGE'),
    ('EVENT_MANAGER', 'SALES_OPEN'),
    ('EVENT_MANAGER', 'ORDER_READ'),
    ('EVENT_MANAGER', 'REPORT_READ'),
    ('FINANCE_MANAGER', 'ORGANIZATION_READ'),
    ('FINANCE_MANAGER', 'PAYMENT_PROFILE_MANAGE'),
    ('FINANCE_MANAGER', 'ORDER_READ'),
    ('FINANCE_MANAGER', 'REPORT_READ'),
    ('BOX_OFFICE_MANAGER', 'EVENT_READ'),
    ('BOX_OFFICE_MANAGER', 'ORDER_READ'),
    ('BOX_OFFICE_MANAGER', 'REPORT_READ'),
    ('GATE_MANAGER', 'EVENT_READ'),
    ('GATE_MANAGER', 'SCANNER_MANAGE'),
    ('SCANNER', 'EVENT_READ'),
    ('ANALYST', 'ORGANIZATION_READ'),
    ('ANALYST', 'EVENT_READ'),
    ('ANALYST', 'ORDER_READ'),
    ('ANALYST', 'REPORT_READ');
