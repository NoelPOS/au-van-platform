# The availability zones this account may use in the region. Reading them
# rather than naming them keeps the module portable and keeps the subnet
# definitions free of region-specific literals.
data "aws_availability_zones" "available" {
  state = "available"

  # Only the zones every account in the region has. Local Zones and
  # Wavelength zones are opt-in, appear only in accounts that enabled them,
  # and would otherwise make the first two names the module slices depend on
  # which account is running it.
  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

# The account this module is applied into. Read rather than configured, so that
# the SSM parameter ARNs the execution role is granted (ADR-012) can be composed
# without an account identifier appearing in a committed file -- the convention
# infra/README.md states.
data "aws_caller_identity" "current" {}

# The address ranges CloudFront makes origin requests from, as an AWS-managed
# prefix list. Read rather than transcribed: the list carries dozens of ranges
# and AWS changes them, so a copy in this repository would be wrong within a
# month. network.tf turns it into the load balancer's only ingress.
data "aws_ec2_managed_prefix_list" "cloudfront_origin_facing" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

# CloudFront's managed cache and origin-request policies, by name. Their ids
# are fixed AWS-wide, but a name says which policy was meant and an id does
# not -- and the whole of #80's third acceptance criterion is a reviewer being
# able to see which policy each behaviour uses.
data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_origin_request_policy" "all_viewer_except_host_header" {
  name = "Managed-AllViewerExceptHostHeader"
}
