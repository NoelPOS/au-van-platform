locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # Two zones, because an application load balancer requires subnets in at
  # least two. More than two would cost nothing here but buys nothing either.
  availability_zones = slice(data.aws_availability_zones.available.names, 0, 2)

  # The two SecureString parameters the owner creates by hand before the first
  # deployment (ADR-012). Terraform neither creates nor reads them: it composes
  # the names and the ARNs, so no value ever reaches a plan, a state file, or
  # this repository. outputs.tf publishes the names so #76's runbook cannot
  # drift from the code.
  jwt_secret_parameter_name                = "${var.ssm_parameter_prefix}/jwt-secret"
  line_channel_access_token_parameter_name = "${var.ssm_parameter_prefix}/line-channel-access-token"

  # A parameter's ARN is the parameter path appended to this prefix with no
  # separator of its own, because the path already starts with a slash.
  ssm_parameter_arn_prefix = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter"

  # The distribution's two origin identifiers. Named here rather than repeated
  # as literals in cdn.tf because target_origin_id is a free string: a typo in
  # one of the four behaviours validates cleanly and fails only at apply.
  cdn_web_origin_id = "web-bucket"
  cdn_api_origin_id = "load-balancer"
}
