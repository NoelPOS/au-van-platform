# What a deployment runbook needs to read back out of an apply.
#
# No secret and no password appears here, and none can: the database password
# is RDS-managed and Terraform holds only its ARN, and the two SecureString
# parameters are named rather than read (ADR-012). An output that resolved a
# secret would write it to state and print it on every apply.

output "demo_url" {
  description = "Where the demo is. The distribution's own *.cloudfront.net name, over HTTPS, serving the web build and proxying /api/* and /actuator/* to the load balancer."
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "web_bucket" {
  description = "Name of the private bucket the web build is uploaded to. Terraform creates the bucket and never its contents: `aws s3 sync web/dist s3://<this>` is an owner step in the deployment runbook."
  value       = aws_s3_bucket.web.id
}

output "cloudfront_distribution_id" {
  description = "Distribution id, for `aws cloudfront create-invalidation` after replacing the bundle. index.html is on a no-cache behaviour, so an invalidation is only needed if the runbook ever caches it."
  value       = aws_cloudfront_distribution.main.id
}

output "load_balancer_dns_name" {
  description = "Public DNS name of the load balancer, which is the distribution's API origin. Not the way to reach the demo: the security group admits only CloudFront."
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
