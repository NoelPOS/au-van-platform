# What a deployment runbook needs to read back out of an apply.
#
# No secret and no password appears here, and none can: the database password
# is RDS-managed and Terraform holds only its ARN, and the two SecureString
# parameters are named rather than read (ADR-012). An output that resolved a
# secret would write it to state and print it on every apply.

output "load_balancer_dns_name" {
  description = "Public DNS name of the load balancer. #75b's CloudFront distribution takes this as its API origin."
  value       = aws_lb.main.dns_name
}

output "ecr_repository_url" {
  description = "Registry URL to tag and push the API image to before deploying."
  value       = aws_ecr_repository.api.repository_url
}

output "database_address" {
  description = "Endpoint host of the RDS instance. Reachable only from the task's security group."
  value       = aws_db_instance.main.address
}

output "payment_proof_bucket" {
  description = "Name of the private payment-proof bucket the API writes to."
  value       = aws_s3_bucket.payment_proofs.id
}

# These two are the point of ADR-012's contract: the runbook creates the
# parameters and the code names them, so the two cannot drift apart. Reading
# them tells an operator what the system needs without telling them any value.
output "required_ssm_parameters" {
  description = "SecureString parameters the owner must create with `aws ssm put-parameter --type SecureString` before the first deployment. The task cannot start without them."
  value = {
    JWT_SECRET                = local.jwt_secret_parameter_name
    LINE_CHANNEL_ACCESS_TOKEN = local.line_channel_access_token_parameter_name
  }
}
