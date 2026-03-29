package agenticvault.common

import rego.v1

# Default deny
default allow := false

# Allow health check endpoints for all agents
allow if {
    input.action == "health_check"
}

# Allow agents to read their own configuration
allow if {
    input.action == "read_config"
    input.resource == concat("/", ["config", input.agent_type])
}

# Deny if agent is not in the approved agent types
deny if {
    not input.agent_type in approved_agent_types
}

approved_agent_types := {
    "ORCHESTRATOR",
    "ACCOUNT",
    "LOANS",
    "CARD",
    "WEALTH",
    "COLLECTIONS",
    "FRAUD",
    "INTERNAL_OPS",
}

# Rate limiting: deny if agent exceeds max requests per minute
deny if {
    input.request_count_per_minute > max_requests_per_minute[input.agent_type]
}

max_requests_per_minute := {
    "ORCHESTRATOR": 1000,
    "ACCOUNT": 500,
    "LOANS": 200,
    "CARD": 500,
    "WEALTH": 200,
    "COLLECTIONS": 300,
    "FRAUD": 2000,
    "INTERNAL_OPS": 100,
}

# All actions must have a valid session
deny if {
    not input.session_id
}

# All actions must have a valid agent ID
deny if {
    not input.agent_id
}

# Decision output
decision := "ALLOW" if {
    allow
    not deny
}

decision := "DENY" if {
    deny
}

decision := "DENY" if {
    not allow
}
