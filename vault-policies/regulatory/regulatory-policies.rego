package agenticvault.regulatory

import rego.v1

# Regulatory compliance policies
# Covers: RBI, SEBI, IRDAI, DPDP Act 2023, TRAI

default allow := false

# Wealth Agent: MUST include risk disclaimer before investment advice
deny_without_disclaimer if {
    input.agent_type == "WEALTH"
    input.action in {"investment_recommendation", "mutual_fund_purchase", "sip_creation"}
    not input.metadata.risk_disclaimer_shown
}

reason := "SEBI regulation: Risk disclaimer must be shown before investment advice" if {
    deny_without_disclaimer
}

# Wealth Agent: Suitability assessment required before product suggestions
deny_without_suitability if {
    input.agent_type == "WEALTH"
    input.action in {"investment_recommendation", "mutual_fund_purchase"}
    not input.metadata.suitability_assessment_completed
}

reason := "SEBI regulation: Customer suitability assessment required before investment recommendation" if {
    deny_without_suitability
}

# Wealth Agent: Cannot guarantee returns
deny if {
    input.agent_type == "WEALTH"
    input.action == "guarantee_returns"
}

# Collections Agent: MUST identify as AI at start of call
deny_without_ai_disclosure if {
    input.agent_type == "COLLECTIONS"
    input.action == "outbound_call"
    not input.metadata.ai_disclosure_made
}

reason := "RBI Fair Practices: AI agent must identify itself as AI at start of call" if {
    deny_without_ai_disclosure
}

# All agents: DPDP Act data minimization - only access data necessary for the action
deny_excess_data if {
    input.agent_type == "ACCOUNT"
    input.action == "get_balance"
    count(input.requested_fields) > 3
}

deny_excess_data if {
    input.agent_type == "COLLECTIONS"
    input.resource in {"medical_records", "family_details", "social_media"}
}

reason := "DPDP Act 2023: Data minimization - requested data exceeds necessity for this action" if {
    deny_excess_data
}

# TRAI: DND compliance - do not contact if on DND registry
deny_dnd if {
    input.agent_type in {"COLLECTIONS", "ORCHESTRATOR"}
    input.action in {"outbound_call", "send_promotional_sms"}
    input.metadata.dnd_registered == true
}

reason := "TRAI regulation: Customer registered on DND, cannot make promotional contact" if {
    deny_dnd
}

# RBI: Customer data must stay in India (data localization)
deny_cross_border if {
    input.metadata.processing_region != "ap-south-1"
    input.resource_type in {"ACCOUNT", "LOAN", "CARD", "INVESTMENT"}
}

reason := "RBI data localization: Customer financial data must be processed within India" if {
    deny_cross_border
}

# SEBI: Investment advisory interactions must be retained for 10 years
audit_retention_years := 10 if {
    input.agent_type == "WEALTH"
}

# RBI: All banking interactions retained for 7 years
audit_retention_years := 7 if {
    input.agent_type != "WEALTH"
}

# Decision output
decision := "DENY" if {
    deny_without_disclaimer
}

decision := "DENY" if {
    deny_without_suitability
}

decision := "DENY" if {
    deny_without_ai_disclosure
}

decision := "DENY" if {
    deny_excess_data
}

decision := "DENY" if {
    deny_dnd
}

decision := "DENY" if {
    deny_cross_border
}

decision := "ALLOW" if {
    not deny_without_disclaimer
    not deny_without_suitability
    not deny_without_ai_disclosure
    not deny_excess_data
    not deny_dnd
    not deny_cross_border
}
