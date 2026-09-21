-- One row per payment-proof submission, so a rejection and the resubmission
-- that follows it in #52 both stay in the history rather than overwriting
-- each other. The image itself lives in object storage (ADR-009); this table
-- holds only the key and the metadata needed to fetch and review it.
--
-- bookings.status and booking_events.event_type are plain VARCHAR, so the new
-- PENDING_PAYMENT, PAYMENT_UNDER_REVIEW and PAYMENT_PROOF_SUBMITTED values
-- need no migration of their own.
CREATE TABLE payment_proofs (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    submitted_by_user_id UUID NOT NULL REFERENCES app_users(id),
    -- payment-proofs/{bookingId}/{timestamp}-{uuid}{ext}. Unique because a
    -- second row on one key would mean two submissions sharing one image.
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT payment_proofs_object_key_unique UNIQUE (object_key),
    CONSTRAINT payment_proofs_size_positive CHECK (size_bytes > 0)
);

-- The review screen in #52 lists a booking's proofs; nothing else reads them.
CREATE INDEX payment_proofs_booking_id_idx ON payment_proofs (booking_id);
