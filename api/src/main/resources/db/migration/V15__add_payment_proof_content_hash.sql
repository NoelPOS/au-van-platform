-- NULL for rows written before V15: their bytes are only in object storage, so they were not backfilled.
ALTER TABLE payment_proofs ADD COLUMN content_sha256 VARCHAR(64);

CREATE INDEX payment_proofs_content_sha256_idx ON payment_proofs (content_sha256);
