-- Money a cancelled booking may owe the student (ADR-017). NONE, DUE or REFUNDED: plain VARCHAR,
-- as bookings.status is. One statement per column: H2 refuses several ADD COLUMN clauses at once.
ALTER TABLE bookings ADD COLUMN refund_status VARCHAR(16) DEFAULT 'NONE' NOT NULL;
ALTER TABLE bookings ADD COLUMN refunded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE bookings ADD COLUMN refunded_by_user_id UUID REFERENCES app_users(id);
ALTER TABLE bookings ADD COLUMN refund_note VARCHAR(500);

CREATE INDEX bookings_refund_status_idx ON bookings (refund_status, updated_at);
