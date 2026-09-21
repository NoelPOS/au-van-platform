-- One row per student per trip: the queue for a trip whose seats are all
-- claimed. ADR-011 records the decision, including the two shapes this table
-- deliberately does not have.
--
-- UNIQUE (trip_id, user_id) is plain, not partial. ADR-006 rejected partial
-- indexes outright because H2 — which the whole suite runs on — does not
-- support them, and a constraint the suite cannot exercise is a constraint the
-- design cannot rest on. One row per student per trip forever is the
-- consequence: leaving and rejoining reuses the row with a fresh joined_at and
-- costs the student their place, which is the only behaviour this constraint
-- permits.
--
-- No position column. Position is derived from joined_at on every read, for the
-- reason seat_claims has no status column: a maintained integer has to be
-- rewritten for every row behind a student who leaves, and a position that has
-- drifted from reality is invisible.
--
-- trip_id carries a foreign key; user_id is a plain UUID column on the mapping
-- with the same app_users foreign key bookings.user_id has (V3). Nothing here
-- reaches for EXCLUDE or JSONB, which H2 also refuses.
CREATE TABLE waitlist_entries (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id),
    user_id UUID NOT NULL REFERENCES app_users(id),
    seats_wanted INTEGER NOT NULL,
    -- WAITING, PROMOTED, FULFILLED, WITHDRAWN, or EXPIRED. Plain VARCHAR, as
    -- bookings.status is, so a new value needs no migration.
    status VARCHAR(32) NOT NULL,
    -- The whole of the ordering rule: ORDER BY joined_at, id.
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Written by the promotion sweep (#69), which is not implemented yet: the
    -- hold it created for this student, and when that hold stops being theirs.
    -- Both are NULL for every row this branch can write.
    promotion_hold_id UUID,
    promotion_expires_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT waitlist_entries_trip_user_unique UNIQUE (trip_id, user_id),
    CONSTRAINT waitlist_entries_seats_wanted_positive CHECK (seats_wanted > 0)
);

-- The candidate query: the next waiting entry on a trip, in join order.
CREATE INDEX waitlist_entries_candidate_idx ON waitlist_entries (trip_id, status, joined_at);

-- The lapse sweep's only predicate: status = 'PROMOTED' AND promotion_expires_at <= now.
CREATE INDEX waitlist_entries_promotion_idx ON waitlist_entries (status, promotion_expires_at);
