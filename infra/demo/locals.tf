locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # Two zones, because an application load balancer requires subnets in at
  # least two. More than two would cost nothing here but buys nothing either.
  availability_zones = slice(data.aws_availability_zones.available.names, 0, 2)
}
