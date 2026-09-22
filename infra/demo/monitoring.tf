# One log group and three alarms -- ADR-007's number, and no dashboard.
#
# These three because each one is a different way the demo is silently broken
# rather than merely slow, and none of them produces a visible symptom the
# operator would otherwise notice in time.

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${local.name_prefix}/api"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "${local.name_prefix}-api"
  }
}

# An alarm needs a destination, and aws_cloudwatch_metric_alarm has no email
# field -- so the budget module's precedent of addressing the subscriber
# directly does not transfer here. The topic and its subscription exist only
# when somebody has asked to be told; with the variable empty, alarm_actions
# is [] and every alarm still shows its state in the console.
resource "aws_sns_topic" "alerts" {
  count = var.alarm_notification_email == "" ? 0 : 1

  name = "${local.name_prefix}-alerts"

  tags = {
    Name = "${local.name_prefix}-alerts"
  }
}

resource "aws_sns_topic_subscription" "alerts_email" {
  count = var.alarm_notification_email == "" ? 0 : 1

  topic_arn = aws_sns_topic.alerts[0].arn
  protocol  = "email"
  endpoint  = var.alarm_notification_email
}

# The task is running and answering, but /actuator/health is not 200 -- which
# is the failure the datasource health indicator is deliberately left enabled
# to produce. Without this alarm it looks like a working deployment.
resource "aws_cloudwatch_metric_alarm" "api_unhealthy_hosts" {
  alarm_name          = "${local.name_prefix}-api-unhealthy-hosts"
  alarm_description   = "The API target group has an unhealthy target."
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"

  # No data means no target registered at all, which the deployment itself
  # already shows; alarming on it would fire through every teardown.
  treat_missing_data = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.api.arn_suffix
  }

  alarm_actions = aws_sns_topic.alerts[*].arn
}

# Errors the application itself returned. A handful over five minutes is the
# difference between one student's unlucky request and a broken deployment.
resource "aws_cloudwatch_metric_alarm" "api_target_5xx" {
  alarm_name          = "${local.name_prefix}-api-5xx"
  alarm_description   = "The API returned 5XX responses through the load balancer."
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanThreshold"

  # The metric is only published when there are errors, so absence is health.
  treat_missing_data = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.api.arn_suffix
  }

  alarm_actions = aws_sns_topic.alerts[*].arn
}

# The one RDS failure that stops writes with no other symptom: the instance is
# up, reachable and healthy, and every INSERT fails. Two GB of the default
# twenty, in bytes, because CloudWatch publishes this metric in bytes.
resource "aws_cloudwatch_metric_alarm" "database_free_storage" {
  alarm_name          = "${local.name_prefix}-db-free-storage"
  alarm_description   = "The database instance is running out of storage."
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 1
  threshold           = 2 * 1024 * 1024 * 1024
  comparison_operator = "LessThanThreshold"

  # Missing rather than breaching: the instance publishes this every minute
  # once it is up, and treating the gap before the first apply finishes as a
  # breach would email the owner about a database that is still being created.
  treat_missing_data = "missing"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.identifier
  }

  alarm_actions = aws_sns_topic.alerts[*].arn
}
