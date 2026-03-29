-- Customer sessions table
CREATE TABLE customer_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(50) NOT NULL,
    verification_type VARCHAR(20) NOT NULL,
    verified_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    device_fingerprint VARCHAR(255),
    CONSTRAINT chk_verification_type CHECK (verification_type IN ('OTP', 'BIOMETRIC', 'SESSION'))
);
CREATE INDEX idx_customer_sessions_customer ON customer_sessions(customer_id);
CREATE INDEX idx_customer_sessions_expires ON customer_sessions(expires_at);

-- JWT keys table
CREATE TABLE jwt_keys (
    key_id VARCHAR(100) PRIMARY KEY,
    public_key TEXT NOT NULL,
    private_key_encrypted TEXT NOT NULL,
    algorithm VARCHAR(20) NOT NULL DEFAULT 'RS256',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX idx_jwt_keys_active ON jwt_keys(is_active, expires_at);
