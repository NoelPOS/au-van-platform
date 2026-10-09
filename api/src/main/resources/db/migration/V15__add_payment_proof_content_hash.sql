-- Hex SHA-256 of the slip's bytes, so the review queue can spot one image sent for several bookings.
-- Rows written before V15 stay NULL: their bytes are only in object storage.
ALTER TABLE payment_proofs ADD COLUMN content_sha256 VARCHAR(64);

CREATE INDEX payment_proofs_content_sha256_idx ON payment_proofs (content_sha256);
