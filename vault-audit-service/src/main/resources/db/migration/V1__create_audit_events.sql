CREATE TABLE IF NOT EXISTS audit_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        VARCHAR(128)  NOT NULL UNIQUE,
    timestamp       TIMESTAMPTZ   NOT NULL,
    agent_id        VARCHAR(128)  NOT NULL,
    instance_id     VARCHAR(256),
    customer_id     VARCHAR(128),
    action          VARCHAR(256)  NOT NULL,
    resource        VARCHAR(512)  NOT NULL,
    policy_result   VARCHAR(32)   NOT NULL,
    policy_ref      VARCHAR(256),
    request_hash    VARCHAR(128),
    response_hash   VARCHAR(128),
    latency_ms      BIGINT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Index for querying by event ID
CREATE UNIQUE INDEX idx_audit_events_event_id ON audit_events (event_id);

-- Index for querying by agent
CREATE INDEX idx_audit_events_agent_id ON audit_events (agent_id);

-- Index for querying by customer
CREATE INDEX idx_audit_events_customer_id ON audit_events (customer_id);

-- Index for time-range queries
CREATE INDEX idx_audit_events_timestamp ON audit_events (timestamp DESC);

-- Composite index for filtered queries
CREATE INDEX idx_audit_events_agent_action ON audit_events (agent_id, action, timestamp DESC);

-- Index for policy result aggregation
CREATE INDEX idx_audit_events_policy_result ON audit_events (policy_result, timestamp DESC);
