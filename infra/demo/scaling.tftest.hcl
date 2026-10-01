mock_provider "aws" {
  override_data {
    target = data.aws_availability_zones.available
    values = { names = ["zone-a", "zone-b"] }
  }

  mock_resource "aws_lb" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-southeast-1:000000000000:loadbalancer/app/test/1" }
  }

  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-southeast-1:000000000000:targetgroup/test/1" }
  }

  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::000000000000:role/test" }
  }

  mock_resource "aws_cloudfront_function" {
    defaults = { arn = "arn:aws:cloudfront::000000000000:function/test" }
  }

  mock_resource "aws_db_instance" {
    defaults = { master_user_secret = [{ secret_arn = "arn:aws:secretsmanager:ap-southeast-1:000000000000:secret:test" }] }
  }
}

variables {
  api_image_tag = "test"
}

run "defaults_run_one_task_and_scale_to_two_on_cpu" {
  command = plan

  assert {
    condition     = aws_ecs_service.api.desired_count == 1
    error_message = "The service must start one task by default."
  }

  assert {
    condition     = aws_appautoscaling_target.api.min_capacity == 1 && aws_appautoscaling_target.api.max_capacity == 2
    error_message = "The scaler must run between one and two tasks by default."
  }

  assert {
    condition     = aws_appautoscaling_policy.api_cpu.target_tracking_scaling_policy_configuration[0].target_value == 60
    error_message = "The scaler must track average CPU at 60%."
  }

  assert {
    condition     = aws_ecs_service.api.deployment_minimum_healthy_percent == 100 && aws_ecs_service.api.deployment_maximum_percent == 200
    error_message = "A deployment must start the replacement task before stopping the old one."
  }
}

run "rejects_a_minimum_below_one" {
  command = plan

  variables {
    api_min_tasks = 0
  }

  expect_failures = [var.api_min_tasks]
}

run "rejects_a_maximum_below_the_minimum" {
  command = plan

  variables {
    api_min_tasks = 2
    api_max_tasks = 1
  }

  expect_failures = [var.api_max_tasks]
}

run "min_of_two_runs_two_tasks_across_both_zones" {
  variables {
    api_min_tasks = 2
  }

  assert {
    condition     = aws_ecs_service.api.desired_count == 2 && aws_appautoscaling_target.api.min_capacity == 2
    error_message = "api_min_tasks must set both the starting count and the scaler's floor."
  }

  assert {
    condition     = toset(aws_ecs_service.api.network_configuration[0].subnets) == toset(aws_subnet.public[*].id)
    error_message = "The service must place tasks in both public subnets."
  }

  assert {
    condition     = toset(aws_subnet.public[*].availability_zone) == toset(["zone-a", "zone-b"])
    error_message = "The public subnets must sit in two availability zones."
  }
}

run "lowering_the_minimum_leaves_the_running_count_to_the_scaler" {
  command = plan

  assert {
    condition     = aws_ecs_service.api.desired_count == 2
    error_message = "An apply must not reset the count the scaler manages."
  }

  assert {
    condition     = aws_appautoscaling_target.api.min_capacity == 1
    error_message = "The new minimum must reach the scaler."
  }
}
