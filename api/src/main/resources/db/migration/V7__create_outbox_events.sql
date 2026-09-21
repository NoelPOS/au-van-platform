-- One row per piece of outbound work a committed transaction owes: today a
-- LINE notification for a booking or payment transition, in #62 and #63 a seat
-- release and a departure reminder. The row is written in the same transaction
-- as the domain change it describes, which is the whole point — the two cannot
-- diverge, because they are one commit (ADR-010).
--
-- Deliberately no foreign keys. aggregate_id and recipient_user_id are
-- references, not relationships: a queue row must not block the deletion of
-- what it describes, and the dispatcher has to tolerate an aggregate that has
-- since gone. A constraint here would also pull this table into the
-- foreign-key-ordered cleanup every integration test already performs, for no
-- correctness this table relies on.
--
-- payload is a small JSON document: today a booking reference and the same
-- sentence booking_events.detail carries. Its widest field is a review note,
-- which PaymentProofReviewService bounds at 500 characters, so the widest
-- payload written here is well under a thousand. VARCHAR(2000) is that with
-- room for the fields #62 and #63 add, and it is bounded rather than unlimited
-- for the reason ADR-008 gives for response_body VARCHAR(4000): a payload that
-- outgrows its column fails at the insert, where it is visible, instead of
-- silently making this table the largest thing in the database.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    -- The booking the event is about, and the student the message is for. The
    -- recipient is frozen at record time rather than re-derived at send time,
    -- so a later change cannot redirect a message that was already owed.
    aggregate_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    payload VARCHAR(2000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL,
    -- When this row may next be claimed. The claim pushes it forward by a
    -- lease, so a worker that dies mid-send leaves a row that simply becomes
    -- due again: SQS's visibility timeout, in one column.
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(1000),
    -- NULL for a state-change event, which legitimately repeats. #63 sets it
    -- for a scheduled reminder, where the unique constraint is what makes
    -- scheduling idempotent — the legacy application's unique (bookingId, type).
    dedupe_key VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT outbox_events_dedupe_key_unique UNIQUE (dedupe_key),
    CONSTRAINT outbox_events_attempts_non_negative CHECK (attempts >= 0)
);

-- The dispatcher's only predicate: status IN (…) AND next_attempt_at <= now.
CREATE INDEX outbox_events_dispatch_idx ON outbox_events (status, next_attempt_at);
