package agenticvault.dataaccess

import rego.v1

# Data access control policies governing which agents can access which data resources.
# Enforces least-privilege access and filters data during inter-agent handoffs.

default allow := false

# ----- ALLOWED RESOURCES PER AGENT TYPE -----

allowed_resources := {
    "ACCOUNT": {
        "account_balance", "account_details", "transaction_history",
        "mini_statement", "account_statement", "fd_details",
        "cheque_book_status", "account_summary",
    },
    "LOANS": {
        "credit_score", "income_data", "loan_details",
        "emi_schedule", "loan_statement", "repayment_history",
        "pre_approval_status", "loan_eligibility",
    },
    "CARD": {
        "card_data_masked", "card_statement", "card_limit",
        "card_rewards", "card_transactions_masked", "card_status",
    },
    "WEALTH": {
        "portfolio_summary", "mutual_fund_holdings", "investment_history",
        "risk_profile", "sip_details", "wealth_statement",
    },
    "COLLECTIONS": {
        "outstanding_amount", "payment_history", "contact_log",
        "settlement_options", "overdue_summary",
    },
    "FRAUD": {
        "transaction_history", "login_history", "device_fingerprints",
        "fraud_alerts", "suspicious_patterns", "ip_log",
    },
    "ORCHESTRATOR": {
        "account_summary", "agent_status", "session_info",
        "routing_context",
    },
    "INTERNAL_OPS": {
        "audit_log", "system_metrics", "agent_health",
        "config_summary",
    },
}

# ----- DENIED RESOURCES PER AGENT TYPE (explicit cross-domain denials) -----

denied_resources := {
    "LOANS": {"card_transactions", "card_numbers", "card_data", "card_data_masked"},
    "CARD": {"loan_details", "salary_info", "income_data", "credit_score", "emi_schedule"},
    "ACCOUNT": {"card_data", "card_numbers", "loan_details", "salary_info"},
    "COLLECTIONS": {"card_data", "card_numbers", "investment_details", "portfolio_summary"},
    "WEALTH": {"card_data", "loan_details", "collections_data"},
}

# ----- INTER-AGENT HANDOFF ALLOWED FIELDS -----
# When handing off data between agents, only these fields may be transferred.

handoff_allowed_fields := {
    "ACCOUNT": {"customer_id", "account_id", "account_type", "status"},
    "LOANS": {"customer_id", "loan_id", "loan_type", "outstanding_amount", "status"},
    "CARD": {"customer_id", "card_last_four", "card_type", "status"},
    "WEALTH": {"customer_id", "portfolio_id", "risk_category"},
    "COLLECTIONS": {"customer_id", "overdue_amount", "days_past_due", "status"},
    "FRAUD": {"customer_id", "alert_id", "alert_type", "severity"},
    "ORCHESTRATOR": {"customer_id", "session_id", "context_summary"},
    "INTERNAL_OPS": {"agent_id", "operation_type", "status"},
}

# ----- DENY RULES -----

# Deny if the agent type is accessing an explicitly denied resource
deny_cross_domain if {
    denied := denied_resources[input.agent_type]
    input.resource in denied
}

# Deny if the resource is not in the agent's allowed list
deny_unauthorized_resource if {
    allowed := allowed_resources[input.agent_type]
    not input.resource in allowed
    input.action in {"read", "query", "fetch", "get"}
}

# Deny inter-agent handoff if fields are not in the allowed set for the receiving agent
deny_handoff_fields if {
    input.action == "handoff_data"
    target_allowed := handoff_allowed_fields[input.target_agent_type]
    some field in input.handoff_fields
    not field in target_allowed
}

deny if { deny_cross_domain }
deny if { deny_unauthorized_resource }
deny if { deny_handoff_fields }

# ----- ALLOW RULES -----

# Allow data access if the resource is in the agent's allowed list
allow if {
    allowed := allowed_resources[input.agent_type]
    input.resource in allowed
    input.customer_id
    input.session_id
    not deny
}

# Allow inter-agent handoff when all fields are permitted for the receiving agent
allow if {
    input.action == "handoff_data"
    input.source_agent_type
    input.target_agent_type
    not deny_handoff_fields
    not deny_cross_domain
    input.session_id
}

# ----- DECISION OUTPUT -----

decision := "ALLOW" if {
    allow
    not deny
}

decision := "DENY" if {
    deny
}

decision := "DENY" if {
    not allow
    not deny
}

# Reason for decision
reason := "Data access permitted for agent type" if {
    decision == "ALLOW"
    input.action != "handoff_data"
}

reason := "Inter-agent data handoff permitted with filtered fields" if {
    decision == "ALLOW"
    input.action == "handoff_data"
}

reason := sprintf("Cross-domain access denied: %s agent cannot access %s", [input.agent_type, input.resource]) if {
    deny_cross_domain
}

reason := sprintf("Resource %s is not in the allowed list for %s agent", [input.resource, input.agent_type]) if {
    deny_unauthorized_resource
    not deny_cross_domain
}

reason := "Inter-agent handoff contains fields not permitted for the receiving agent type" if {
    deny_handoff_fields
    not deny_cross_domain
    not deny_unauthorized_resource
}

reason := "Action denied by data access control policy" if {
    decision == "DENY"
    not deny_cross_domain
    not deny_unauthorized_resource
    not deny_handoff_fields
}
