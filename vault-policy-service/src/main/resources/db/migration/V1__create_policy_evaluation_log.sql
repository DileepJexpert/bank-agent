CREATE TABLE policy_evaluation_log (
    eval_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id VARCHAR(100) NOT NULL,
    agent_type VARCHAR(50) NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    customer_id_masked VARCHAR(50),
    resource_type VARCHAR(50),
    decision VARCHAR(20) NOT NULL,
    reason TEXT,
    policy_ref VARCHAR(200),
    evaluation_time_ms INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_policy_eval_agent ON policy_evaluation_log(agent_id, created_at);
CREATE INDEX idx_policy_eval_decision ON policy_evaluation_log(decision, created_at);
