package agenticvault.temporal

import rego.v1

# Temporal policies for collections and time-restricted operations.
# All times are evaluated in IST (UTC+05:30).

default allow := false

# IST offset in nanoseconds from UTC (5 hours 30 minutes)
ist_offset_hours := 5
ist_offset_minutes := 30

# Compute IST hour from the UTC timestamp provided in input.
# input.timestamp_utc_hour and input.timestamp_utc_minute are expected as integers.
ist_hour := hour if {
    total_minutes := input.timestamp_utc_hour * 60 + input.timestamp_utc_minute + ist_offset_hours * 60 + ist_offset_minutes
    hour := (total_minutes / 60) % 24
}

# Collections calls: DENY if IST time is before 08:00 or after 19:00 (7 PM)
deny_collections_outside_hours if {
    input.agent_type == "COLLECTIONS"
    input.action == "initiate_call"
    ist_hour < 8
}

deny_collections_outside_hours if {
    input.agent_type == "COLLECTIONS"
    input.action == "initiate_call"
    ist_hour >= 19
}

# Collections contact frequency: DENY if weekly contact count >= 3
deny_collections_frequency if {
    input.agent_type == "COLLECTIONS"
    input.action == "initiate_call"
    input.weekly_contact_count >= 3
}

# Allow collections calls within permitted hours and frequency
allow if {
    input.agent_type == "COLLECTIONS"
    input.action == "initiate_call"
    not deny_collections_outside_hours
    not deny_collections_frequency
    input.customer_id
    input.session_id
}

# Allow collections SMS within permitted hours
allow if {
    input.agent_type == "COLLECTIONS"
    input.action == "send_reminder_sms"
    not deny_collections_outside_hours
    input.customer_id
    input.session_id
}

# Deny any collections action outside business hours
deny if {
    deny_collections_outside_hours
}

# Deny collections if frequency exceeded
deny if {
    deny_collections_frequency
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
    not deny
}

# Reason for decision
reason := "Action allowed within permitted hours and contact frequency" if {
    decision == "ALLOW"
}

reason := "Collections calls are only permitted between 08:00 and 19:00 IST as per RBI guidelines" if {
    deny_collections_outside_hours
}

reason := "Weekly contact frequency limit (3) reached for this customer" if {
    deny_collections_frequency
    not deny_collections_outside_hours
}

reason := "Action denied by temporal policy" if {
    decision == "DENY"
    not deny_collections_outside_hours
    not deny_collections_frequency
}
