-- Add hash chain column for tamper-evident audit trail
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS prev_event_hash VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS correlation_id UUID;

CREATE INDEX IF NOT EXISTS idx_audit_correlation ON audit_events(correlation_id);

-- Immutability trigger: prevent UPDATE and DELETE on audit_events.
-- This ensures the audit trail is append-only for regulatory compliance (RBI/SEBI).
CREATE OR REPLACE FUNCTION prevent_audit_modification() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit events cannot be modified or deleted. This table is append-only for regulatory compliance.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_immutable
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_modification();
