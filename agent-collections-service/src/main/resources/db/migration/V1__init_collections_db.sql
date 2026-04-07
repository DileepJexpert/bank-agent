CREATE TABLE IF NOT EXISTS collections_queue (
    queue_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(50) NOT NULL,
    product_type VARCHAR(50) NOT NULL,
    overdue_amount DECIMAL(15,2) NOT NULL,
    days_overdue INTEGER NOT NULL,
    priority INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS collections_interactions (
    interaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(50) NOT NULL,
    call_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    duration INTEGER DEFAULT 0,
    outcome VARCHAR(50),
    offer_made VARCHAR(255),
    discount_pct DECIMAL(5,2),
    promise_to_pay_date DATE,
    transcript TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_collections_queue_status ON collections_queue(status);
CREATE INDEX idx_collections_queue_customer ON collections_queue(customer_id);
CREATE INDEX idx_collections_interactions_customer ON collections_interactions(customer_id);
