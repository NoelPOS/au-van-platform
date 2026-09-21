CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id),
    user_id UUID NOT NULL REFERENCES app_users(id),
    passenger_name VARCHAR(255) NOT NULL,
    passenger_phone VARCHAR(50) NOT NULL,
    total_fare NUMERIC(10, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT bookings_total_fare_non_negative CHECK (total_fare >= 0)
);

CREATE TABLE booking_seats (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    trip_seat_id UUID NOT NULL REFERENCES trip_seats(id),
    CONSTRAINT booking_seats_seat_unique UNIQUE (booking_id, trip_seat_id)
);

-- One row per claimed seat, covering both a short-lived hold and a booked seat.
-- UNIQUE (trip_seat_id) is the only authority that prevents overselling: a seat
-- is claimable exactly when no row exists for it. Rows are deleted on release
-- and when an expired claim is reclaimed, so there is no status column to drift.
-- expires_at is always set; a claim blocks its seat while
-- booking_id IS NOT NULL OR expires_at > now.
CREATE TABLE seat_claims (
    id UUID PRIMARY KEY,
    trip_seat_id UUID NOT NULL REFERENCES trip_seats(id),
    user_id UUID NOT NULL REFERENCES app_users(id),
    hold_id UUID NOT NULL,
    booking_id UUID REFERENCES bookings(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT seat_claims_trip_seat_unique UNIQUE (trip_seat_id)
);

CREATE INDEX seat_claims_hold_id_idx ON seat_claims (hold_id);
CREATE INDEX seat_claims_user_id_idx ON seat_claims (user_id);
CREATE INDEX seat_claims_expires_at_idx ON seat_claims (expires_at);

CREATE TABLE booking_events (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    actor_user_id UUID REFERENCES app_users(id),
    event_type VARCHAR(64) NOT NULL,
    detail VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX booking_events_booking_id_idx ON booking_events (booking_id);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    endpoint VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT idempotency_keys_scope_unique UNIQUE (user_id, endpoint, idempotency_key)
);
