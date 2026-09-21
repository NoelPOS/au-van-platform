# The account's spending guardrail.
#
# This is a root module of its own, with its own state, so that destroying the
# demo cannot destroy the alarm. A guardrail that is torn down with the thing
# it guards is only present when it is not needed.
#
# Notifications address the subscriber's email directly. An SNS topic would add
# a topic, a topic policy, and a subscription confirmation for one recipient,
# and aws_budgets_budget takes the address itself.

data "aws_caller_identity" "current" {}

resource "aws_budgets_budget" "monthly_cost" {
  account_id   = data.aws_caller_identity.current.account_id
  name         = var.budget_name
  budget_type  = "COST"
  limit_amount = var.monthly_limit_usd
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # Half the month's ceiling, early enough to still act on it.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 50
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.notification_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.notification_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.notification_email]
  }

  # The forecast is the one that arrives before the money is spent: it fires
  # when the month is projected to end over the ceiling, not after it has.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.notification_email]
  }
}
