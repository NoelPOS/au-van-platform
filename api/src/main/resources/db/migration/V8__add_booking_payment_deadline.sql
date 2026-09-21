-- When an unpaid booking stops holding its seats. ADR-010 decided that every
-- booking carries its own deadline rather than the sweep deriving one per
-- status: three statuses then collapse into one predicate with no join, and the
-- student can be shown the deadline instead of guessing at it.
--
-- Nullable, and NULL means "never expires". CONFIRMED and CANCELLED rows are
-- terminal and correctly carry NULL, and rows already existed when this ran, so
-- NOT NULL was never available.
--
-- One statement per column: H2 in PostgreSQL mode, which the test suite runs
-- on, refuses several ADD COLUMN clauses in one ALTER TABLE (V6 records the
-- same thing). bookings.status and booking_events.event_type are plain VARCHAR
-- (V3), so the new EXPIRED event type needs no migration of its own.
ALTER TABLE bookings ADD COLUMN payment_deadline_at TIMESTAMP WITH TIME ZONE;

-- Rows that already exist in a non-terminal status would otherwise keep a NULL
-- deadline and never expire, which silently reproduces the gap ADR-009 named
-- and this migration exists to close. The bound is the same one the application
-- applies at creation, with booking.payment-window and booking.departure-cutoff
-- at their defaults: two hours from now, or an hour before departure, whichever
-- comes first. Written with the SQL-standard interval literal, which both
-- PostgreSQL and H2 accept.
UPDATE bookings
SET payment_deadline_at = LEAST(
        CURRENT_TIMESTAMP + INTERVAL '2' HOUR,
        (SELECT trip.departure_at FROM trips trip WHERE trip.id = bookings.trip_id) - INTERVAL '1' HOUR)
WHERE status IN ('PENDING_PAYMENT', 'PAYMENT_UNDER_REVIEW', 'PAYMENT_REJECTED');

-- The sweep's only predicate:
--   status IN ('PENDING_PAYMENT','PAYMENT_UNDER_REVIEW','PAYMENT_REJECTED')
--     AND payment_deadline_at <= now
-- Leading on status because it is the more selective of the two once most
-- bookings are CONFIRMED or CANCELLED.
CREATE INDEX bookings_payment_deadline_idx ON bookings (status, payment_deadline_at);
