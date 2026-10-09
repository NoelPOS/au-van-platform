-- A booking under payment review no longer expires (ADR-016), so it carries no deadline.
UPDATE bookings SET payment_deadline_at = NULL WHERE status = 'PAYMENT_UNDER_REVIEW';
