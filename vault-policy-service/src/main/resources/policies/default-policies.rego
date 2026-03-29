package agent.authz

import rego.v1

# Default deny
default allow := false
default escalate := false
default reason := "Action denied by default policy"
default policy_ref := "default/deny"

# Agent type to allowed actions mapping
agent_permissions := {
    "account_agent": {"account:read", "account:balance", "account:statement", "account:mini_statement"},
    "payment_agent": {"payment:initiate", "payment:status", "payment:cancel", "account:read"},
    "loan_agent": {"loan:inquiry", "loan:apply", "loan:status", "account:read"},
    "support_agent": {"ticket:create", "ticket:read", "ticket:update", "account:read", "customer:read"},
    "orchestrator": {"agent:invoke", "agent:status", "workflow:manage", "account:read"}
}

# High-risk actions requiring escalation
high_risk_actions := {"payment:initiate", "loan:apply", "account:close"}

# Maximum transaction amount thresholds by agent type (in INR)
amount_thresholds := {
    "payment_agent": 500000,
    "loan_agent": 5000000
}

# Allow if agent type has permission for the requested action
allow if {
    permitted_actions := agent_permissions[input.agent_type]
    input.action in permitted_actions
    not requires_escalation
}

# Escalate high-risk actions above threshold
escalate if {
    requires_escalation
}

# Determine reason for allow
reason := msg if {
    allow
    msg := sprintf("Action %s allowed for agent type %s", [input.action, input.agent_type])
}

# Determine reason for deny - unknown agent type
reason := msg if {
    not allow
    not escalate
    not agent_permissions[input.agent_type]
    msg := sprintf("Unknown agent type: %s", [input.agent_type])
}

# Determine reason for deny - action not permitted
reason := msg if {
    not allow
    not escalate
    agent_permissions[input.agent_type]
    permitted_actions := agent_permissions[input.agent_type]
    not input.action in permitted_actions
    msg := sprintf("Action %s not permitted for agent type %s", [input.action, input.agent_type])
}

# Determine reason for escalation
reason := msg if {
    escalate
    msg := sprintf("High-risk action %s requires human approval", [input.action])
}

# Policy reference for allow
policy_ref := "agent/authz/allow" if {
    allow
}

# Policy reference for escalation
policy_ref := "agent/authz/escalate" if {
    escalate
}

# Helper: check if action requires escalation
requires_escalation if {
    input.action in high_risk_actions
    amount := object.get(input.context, "amount", 0)
    threshold := amount_thresholds[input.agent_type]
    amount > threshold
}
