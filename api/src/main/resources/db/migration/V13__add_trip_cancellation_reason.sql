-- Why an administrator cancelled a trip (ADR-017). NULL on every trip that is not CANCELLED.
ALTER TABLE trips ADD COLUMN cancellation_reason VARCHAR(300);
