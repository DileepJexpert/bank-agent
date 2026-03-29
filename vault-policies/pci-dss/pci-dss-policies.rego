package agenticvault.pcidss

import rego.v1

# PCI-DSS compliance policies for card data protection.
# Enforces masking, namespace isolation, and prohibits logging of sensitive card data.

default allow := false

# Sensitive fields that must never be logged or transmitted in clear text
sensitive_fields := {"cvv", "pin", "card_number_full", "expiry_cvv", "track_data", "magnetic_stripe"}

# Fields that indicate unmasked card data
unmasked_card_indicators := {"card_number", "primary_account_number", "pan"}

# ----- DENY RULES -----

# ANY agent: DENY logging of full card number, CVV, or PIN
deny_sensitive_logging if {
    input.action == "log_data"
    some field in sensitive_fields
    field in object.keys(input.data_fields)
}

deny_sensitive_logging if {
    input.action == "log_data"
    some field in unmasked_card_indicators
    field in object.keys(input.data_fields)
    not input.data_fields[field] == "masked"
}

# DENY any action that exposes card data outside PCI scope (non-PCI namespace)
deny_pci_scope_violation if {
    input.agent_type == "CARD"
    input.resource == "card_data"
    input.namespace != "pci-dss"
}

# Card Agent: MUST run in pci-dss namespace
deny_card_namespace if {
    input.agent_type == "CARD"
    input.namespace != "pci-dss"
}

# DENY if card_number field is present and not masked
deny_unmasked_card if {
    some field in unmasked_card_indicators
    field in object.keys(input.data_fields)
    not is_masked(input.data_fields[field])
}

# DENY inter-agent transfer of raw card data
deny_card_data_leak if {
    input.action == "handoff_data"
    input.source_agent_type == "CARD"
    input.target_agent_type != "CARD"
    some field in sensitive_fields
    field in object.keys(input.handoff_fields)
}

deny_card_data_leak if {
    input.action == "handoff_data"
    input.source_agent_type == "CARD"
    input.target_agent_type != "CARD"
    some field in unmasked_card_indicators
    field in object.keys(input.handoff_fields)
}

# Aggregate deny
deny if { deny_sensitive_logging }
deny if { deny_pci_scope_violation }
deny if { deny_card_namespace }
deny if { deny_unmasked_card }
deny if { deny_card_data_leak }

# ----- ALLOW RULES -----

# Card Agent: allow card operations within PCI namespace
allow if {
    input.agent_type == "CARD"
    input.namespace == "pci-dss"
    input.action in {"get_card_details", "block_card", "unblock_card", "get_card_statement", "set_card_limit"}
    input.customer_id
    input.session_id
    not deny
}

# Card Agent: allow viewing masked card data
allow if {
    input.agent_type == "CARD"
    input.namespace == "pci-dss"
    input.action == "view_card_data"
    input.data_masking_applied == true
    input.customer_id
    input.session_id
    not deny
}

# ----- HELPER FUNCTIONS -----

# Check if a value appears to be masked (contains asterisks or is explicitly marked)
is_masked(value) if {
    contains(value, "****")
}

is_masked(value) if {
    value == "masked"
}

is_masked(value) if {
    value == "REDACTED"
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
reason := "Action allowed within PCI-DSS compliant scope" if {
    decision == "ALLOW"
}

reason := "PCI-DSS violation: sensitive card data (CVV/PIN/full card number) must not be logged" if {
    deny_sensitive_logging
}

reason := "PCI-DSS violation: Card Agent must operate in pci-dss namespace" if {
    deny_card_namespace
    not deny_sensitive_logging
}

reason := "PCI-DSS violation: card data must not be exposed outside PCI scope" if {
    deny_pci_scope_violation
    not deny_card_namespace
    not deny_sensitive_logging
}

reason := "PCI-DSS violation: card number must be masked before processing" if {
    deny_unmasked_card
    not deny_pci_scope_violation
    not deny_card_namespace
    not deny_sensitive_logging
}

reason := "PCI-DSS violation: raw card data cannot be transferred to non-CARD agents" if {
    deny_card_data_leak
    not deny_unmasked_card
    not deny_pci_scope_violation
    not deny_card_namespace
    not deny_sensitive_logging
}

reason := "Action denied by PCI-DSS policy" if {
    decision == "DENY"
    not deny_sensitive_logging
    not deny_card_namespace
    not deny_pci_scope_violation
    not deny_unmasked_card
    not deny_card_data_leak
}
