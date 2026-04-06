CREATE TABLE IF NOT EXISTS customer_profiles (
    customer_id VARCHAR(50) PRIMARY KEY,
    avg_amount DOUBLE PRECISION DEFAULT 0,
    max_amount DOUBLE PRECISION DEFAULT 0,
    avg_daily_count DOUBLE PRECISION DEFAULT 0,
    common_devices JSONB DEFAULT '[]'::jsonb,
    common_merchants JSONB DEFAULT '[]'::jsonb,
    common_locations JSONB DEFAULT '[]'::jsonb,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS fraud_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    txn_id VARCHAR(100) NOT NULL,
    customer_id VARCHAR(50) NOT NULL,
    risk_score DOUBLE PRECISION NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    action_taken VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_fraud_events_customer ON fraud_events(customer_id);
CREATE INDEX idx_fraud_events_txn ON fraud_events(txn_id);
CREATE INDEX idx_fraud_events_level ON fraud_events(risk_level);
