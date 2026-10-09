-- An administrator lifting a student's booking cool-down (ADR-016). Append-only, so it is
-- also the audit of who lifted it and when; expiries before a student's latest clear stop counting.
CREATE TABLE booking_cooldown_clears (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    cleared_by_user_id UUID NOT NULL REFERENCES app_users(id),
    cleared_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX booking_cooldown_clears_user_idx ON booking_cooldown_clears (user_id, cleared_at);
