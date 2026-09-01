ALTER TABLE ticket_credential DROP CONSTRAINT ticket_credential_active_unique;

CREATE UNIQUE INDEX ticket_credential_active_unique
    ON ticket_credential (ticket_entitlement_id)
    WHERE status = 'ACTIVE';
