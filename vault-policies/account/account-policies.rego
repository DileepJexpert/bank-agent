package agenticvault.account

import rego.v1

# Account Agent specific policies

default allow := false

# Allow balance inquiry
allow if {
    input.agent_type == "ACCOUNT"
    input.action == "get_balance"
    valid_customer_context
}

# Allow mini statement
allow if {
    input.agent_type == "ACCOUNT"
    input.action == "get_mini_statement"
    valid_customer_context
}

# Allow transaction history
allow if {
    input.agent_type == "ACCOUNT"
    input.action == "get_transaction_history"
    valid_customer_context
}

# Allow account details
allow if {
    input.agent_type == "ACCOUNT"
    input.action == "get_account_details"
    valid_customer_context
}

# Allow cheque book request
allow if {
    input.agent_type == "ACCOUNT"
    input.action == "request_cheque_book"
    valid_customer_context
}

# Allow statement generation
allow if {
    input.agent_type == "ACCOUNT"
    input.action == "generate_statement"
    valid_customer_context
}

# FD creation - auto approve up to 10 lakh
allow if {
    input.agent_type == "ACCOUNT"
    input.action == "create_fd"
    valid_customer_context
    input.amount <= 1000000
}

# FD creation above 10 lakh requires escalation
escalate if {
    input.agent_type == "ACCOUNT"
    input.action == "create_fd"
    valid_customer_context
    input.amount > 1000000
}

# Account closure requires maker-checker for amounts above 10 lakh
escalate if {
    input.agent_type == "ACCOUNT"
    input.action == "close_account"
    input.account_balance > 1000000
}

# Allow account closure for balances up to 10 lakh
allow if {
    input.agent_type == "ACCOUNT"
    input.action == "close_account"
    valid_customer_context
    input.account_balance <= 1000000
}

# Transfer limits
allow if {
    input.agent_type == "ACCOUNT"
    input.action == "initiate_transfer"
    valid_customer_context
    input.amount <= 200000
}

escalate if {
    input.agent_type == "ACCOUNT"
    input.action == "initiate_transfer"
    input.amount > 200000
}

# Deny access to card data from account agent
deny if {
    input.agent_type == "ACCOUNT"
    input.resource == "card_data"
}

# Deny access to loan data from account agent
deny if {
    input.agent_type == "ACCOUNT"
    input.resource == "loan_data"
}

# Helper: valid customer context
valid_customer_context if {
    input.customer_id
    input.session_id
    input.agent_id
}

# Decision output
decision := "ALLOW" if {
    allow
    not deny
    not escalate
}

decision := "ESCALATE" if {
    escalate
    not deny
}

decision := "DENY" if {
    deny
}

decision := "DENY" if {
    not allow
    not escalate
}

# Reason for decision
reason := "Action allowed by account policy" if {
    decision == "ALLOW"
}

reason := "Action requires human approval - threshold exceeded" if {
    decision == "ESCALATE"
}

reason := "Action denied by account policy" if {
    decision == "DENY"
}
