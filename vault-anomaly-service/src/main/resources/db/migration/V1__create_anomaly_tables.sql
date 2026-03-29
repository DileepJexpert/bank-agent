-- Anomaly Events table
CREATE TABLE anomaly_events (
    alert_id        UUID PRIMARY KEY,
    severity        VARCHAR(20) NOT NULL,
    anomaly_type    VARCHAR(50) NOT NULL,
    agent_id        VARCHAR(255) NOT NULL,
    instance_id     VARCHAR(255),
    details         JSONB,
    detected_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMP WITH TIME ZONE,
    resolution_notes TEXT,

    CONSTRAINT chk_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_anomaly_type CHECK (anomaly_type IN ('HIGH_RATE', 'UNKNOWN_ACTION', 'COORDINATED_ACCESS', 'PROMPT_INJECTION'))
);

CREATE INDEX idx_anomaly_events_severity ON anomaly_events (severity);
CREATE INDEX idx_anomaly_events_anomaly_type ON anomaly_events (anomaly_type);
CREATE INDEX idx_anomaly_events_agent_id ON anomaly_events (agent_id);
CREATE INDEX idx_anomaly_events_detected_at ON anomaly_events (detected_at DESC);
CREATE INDEX idx_anomaly_events_resolved_at ON anomaly_events (resolved_at) WHERE resolved_at IS NULL;

-- Agent Baselines table
CREATE TABLE agent_baselines (
    agent_type          VARCHAR(255) PRIMARY KEY,
    avg_requests_per_min DOUBLE PRECISION NOT NULL DEFAULT 0,
    stddev              DOUBLE PRECISION NOT NULL DEFAULT 0,
    common_action_types JSONB NOT NULL DEFAULT '[]'::JSONB,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agent_baselines_updated_at ON agent_baselines (updated_at);
