# The payment-proof bucket (ADR-009): private, API-brokered, never handed to a
# browser.
#
# Created unconditionally, which corrects ADR-007's table. That row deferred
# the bucket "once the payment and notification work has designed their
# interfaces"; they have -- ADR-009 chose the private API-brokered bucket and
# #51 shipped S3PaymentProofStorage against it. A demo with the bucket switched
# off has a payment flow that 500s on the first upload, and a demo's worth of
# JPEGs is storage-priced at cents.

resource "aws_s3_bucket" "payment_proofs" {
  # A prefix rather than a name: bucket names are globally unique, so AWS
  # appends the suffix instead of an operator having to invent a free name.
  # Everything that needs the result reads it from this resource, and
  # outputs.tf publishes it.
  bucket_prefix = "${local.name_prefix}-payment-proofs-"

  # Wrong for production and right for a demo that must be removable in one
  # command: a bucket still holding objects refuses to be destroyed, and #76's
  # teardown runbook should not need a manual empty first. The objects are
  # payment slips from a walkthrough, not records anyone keeps.
  force_destroy = true

  tags = {
    Name = "${local.name_prefix}-payment-proofs"
  }
}

# Nothing here is ever public. The API reads objects back and serves them to an
# administrator over an authenticated request; no browser ever addresses the
# bucket.
resource "aws_s3_bucket_public_access_block" "payment_proofs" {
  bucket = aws_s3_bucket.payment_proofs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# No ACLs at all, which is what lets S3PaymentProofStorage put an object with
# no ACL and no public-read grant and have that be the only possible outcome.
resource "aws_s3_bucket_ownership_controls" "payment_proofs" {
  bucket = aws_s3_bucket.payment_proofs.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "payment_proofs" {
  bucket = aws_s3_bucket.payment_proofs.id

  rule {
    apply_server_side_encryption_by_default {
      # SSE-S3 rather than SSE-KMS: encryption at rest with no key to manage,
      # no per-request KMS charge, and no kms:Decrypt grant to add to the task
      # role.
      sse_algorithm = "AES256"
    }
  }
}

# Stated rather than left to the default, because "unversioned" is a decision
# here: a payment proof is written once and never rewritten, so versions would
# only accumulate storage and give force_destroy more to delete.
resource "aws_s3_bucket_versioning" "payment_proofs" {
  bucket = aws_s3_bucket.payment_proofs.id

  versioning_configuration {
    status = "Disabled"
  }
}
