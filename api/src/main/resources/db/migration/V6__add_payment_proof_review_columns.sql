-- The administrator's decision on a payment proof: who made it, when, and the
-- note that goes with it. All three are nullable because a SUBMITTED row has
-- no decision yet, and the CHECK below is what keeps that from meaning "a
-- decided proof may forget who decided it".
--
-- One statement per column: H2 in PostgreSQL mode, which the test suite runs
-- on, refuses several ADD COLUMN clauses in one ALTER TABLE.
--
-- bookings.status and booking_events.event_type are plain VARCHAR (V3), so the
-- new PAYMENT_REJECTED status and the PAYMENT_APPROVED/PAYMENT_REJECTED event
-- types need no migration of their own.
ALTER TABLE payment_proofs ADD COLUMN reviewed_by_user_id UUID REFERENCES app_users(id);
ALTER TABLE payment_proofs ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE payment_proofs ADD COLUMN review_note VARCHAR(500);

-- The database, not a comment, says a decision always names who and when.
ALTER TABLE payment_proofs
    ADD CONSTRAINT payment_proofs_review_recorded CHECK (
        (status = 'SUBMITTED' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL)
        OR (status <> 'SUBMITTED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL));

-- The review queue's only predicate.
CREATE INDEX payment_proofs_status_idx ON payment_proofs (status);
