-- Baseline schema migration. Domain tables are introduced in feature-specific migrations.
CREATE TABLE schema_metadata (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    application_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_metadata (singleton, application_name)
VALUES (TRUE, 'ticketing-platform');
