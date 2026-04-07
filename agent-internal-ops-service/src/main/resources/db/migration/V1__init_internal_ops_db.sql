-- Internal Ops Agent - Database Schema
-- Spring Batch tables are auto-created via spring.batch.jdbc.initialize-schema=always

CREATE TABLE IF NOT EXISTS mis_reports (
    report_id VARCHAR(100) PRIMARY KEY,
    branch_id VARCHAR(50) NOT NULL,
    branch_name VARCHAR(255),
    report_date DATE NOT NULL,
    total_transactions INTEGER DEFAULT 0,
    total_credit DECIMAL(15,2) DEFAULT 0,
    total_debit DECIMAL(15,2) DEFAULT 0,
    transactions_by_type JSONB DEFAULT '{}'::jsonb,
    status VARCHAR(20) DEFAULT 'PENDING',
    generated_by VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS reconciliation_runs (
    reconciliation_id VARCHAR(100) PRIMARY KEY,
    run_date DATE NOT NULL,
    matched_count INTEGER DEFAULT 0,
    mismatch_count INTEGER DEFAULT 0,
    pending_count INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING',
    summary TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_mis_reports_branch ON mis_reports(branch_id);
CREATE INDEX idx_mis_reports_date ON mis_reports(report_date);
CREATE INDEX idx_reconciliation_date ON reconciliation_runs(run_date);
