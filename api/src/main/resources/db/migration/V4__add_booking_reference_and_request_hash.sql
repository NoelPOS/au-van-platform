-- Everything V3 left out that issue #25 needs. All four tables are still empty,
-- so the two NOT NULL columns need no default and no backfill.

-- The customer-facing booking code. Payment and notification messages key off it
-- rather than off the primary key, so it is unique and short enough to read out.
ALTER TABLE bookings ADD COLUMN reference VARCHAR(32) NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT bookings_reference_unique UNIQUE (reference);

-- The fingerprint of the request a stored response was produced for. Without it
-- a replayed key cannot be told from a key reused for a different payload.
ALTER TABLE idempotency_keys ADD COLUMN request_hash VARCHAR(64) NOT NULL;

-- The two predicates #25 adds: a student's own bookings, and the claims a
-- cancelled booking has to release. V3 indexed neither.
CREATE INDEX bookings_user_id_idx ON bookings (user_id);
CREATE INDEX seat_claims_booking_id_idx ON seat_claims (booking_id);
