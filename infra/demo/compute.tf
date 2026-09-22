# The one Fargate service, running the API image and nothing else.
#
# Not the web image. ADR-007 serves the web build from S3 as CloudFront's
# default behaviour, so an nginx task in front of the same static files would be
# a second task, a second target group and a second set of health checks to
# serve bytes S3 already serves -- exactly the line ADR-004 exists to refuse.
# web/Dockerfile keeps earning its place in compose.yaml and in Container
# checks; CloudFront is the third implementation of the same contract.

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  # Container Insights bills per metric. Three CloudWatch alarms are what
  # ADR-007 chose for this demo, and monitoring.tf has them.
  setting {
    name  = "containerInsights"
    value = "disabled"
  }

  tags = {
    Name = "${local.name_prefix}-cluster"
  }
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${local.name_prefix}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  # Stated rather than defaulted, because the operator's `docker build` has to
  # match it. ARM64 Fargate is cheaper, and moving to it means building the
  # image for arm64 as well as changing this line.
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name      = "api"
      image     = "${aws_ecr_repository.api.repository_url}:${var.api_image_tag}"
      essential = true

      portMappings = [
        {
          containerPort = var.app_port
          protocol      = "tcp"
        }
      ]

      # Configuration, not credentials (ADR-012). Every value here is readable
      # from `ecs:DescribeTaskDefinition`, which is exactly why nothing secret
      # is in this list.
      #
      # ADMIN_BOOTSTRAP_LINE_SUBJECT is deliberately absent. The image carries
      # only app.jar, so the first administrator is created by a one-off
      # `aws ecs run-task` against this same task definition with a command and
      # an environment override -- #76's runbook owns that. Baking a subject in
      # here would make the definition single-purpose and the override a lie.
      environment = [
        {
          # Composed from the instance rather than taken from a variable, so
          # the URL cannot drift from the database this module created.
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${aws_db_instance.main.db_name}"
        },
        {
          name  = "SPRING_DATASOURCE_USERNAME"
          value = aws_db_instance.main.username
        },
        {
          # Required, with no default, because being same-origin does not
          # exempt a request from Spring's CORS filter: a browser sends Origin
          # on every non-GET/HEAD request even to its own origin, and
          # CorsUtils.isCorsRequest keys on the header being present. An empty
          # value here would 403 every POST before authentication. #75b sets
          # this from its distribution's domain; until then it is the load
          # balancer's own origin. See variables.tf for the full reasoning.
          name  = "CORS_ALLOWED_ORIGINS"
          value = var.cors_allowed_origins
        },
        {
          # A LINE Login channel's audience identifier, not a credential --
          # web/.env.example already treats the LIFF id as public.
          name  = "LINE_CHANNEL_ID"
          value = var.line_channel_id
        },
        {
          name  = "PAYMENT_PROOF_BUCKET"
          value = aws_s3_bucket.payment_proofs.id
        },
        {
          name  = "PAYMENT_PROOF_REGION"
          value = var.aws_region
        }
      ]

      # PAYMENT_PROOF_ENDPOINT is deliberately unset, which is what points the
      # S3 client at real S3, and no AWS_ACCESS_KEY_ID is set, which is what
      # makes the SDK's default chain resolve the task role instead.
      #
      # These three are resolved by the ECS agent before the container starts,
      # using the execution role. A missing parameter fails the task with a
      # resource-initialization error naming it, which is the intended failure
      # (ADR-012): there is no placeholder that could quietly run in its place.
      secrets = [
        {
          name      = "POSTGRES_PASSWORD"
          valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::"
        },
        {
          name      = "JWT_SECRET"
          valueFrom = "${local.ssm_parameter_arn_prefix}${local.jwt_secret_parameter_name}"
        },
        {
          name      = "LINE_CHANNEL_ACCESS_TOKEN"
          valueFrom = "${local.ssm_parameter_arn_prefix}${local.line_channel_access_token_parameter_name}"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"

        options = {
          awslogs-group         = aws_cloudwatch_log_group.api.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "api"
        }
      }
    }
  ])

  tags = {
    Name = "${local.name_prefix}-api"
  }
}

resource "aws_ecs_service" "api" {
  name            = "${local.name_prefix}-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  # Long enough for Flyway plus the Spring context. api/Dockerfile's own probe
  # allows 60s before a failure counts, and this runs at half a vCPU, so 120s.
  # Too short and ECS kills and restarts the task forever, which reads exactly
  # like a broken image rather than a slow one.
  health_check_grace_period_seconds = 120

  # One task at a time, and a few seconds of downtime during a deployment. The
  # alternative is paying for a second task to overlap the first, which is the
  # wrong trade for a demo.
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  # assign_public_ip is mandatory, not a preference. These are public subnets
  # with no NAT gateway and no VPC endpoints (network.tf), so the internet
  # gateway is the task's only route to ECR, Secrets Manager, SSM, CloudWatch
  # Logs and the LINE API. With it false the task cannot even pull its image.
  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.task.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = var.app_port
  }

  # A target group has to be attached to a load balancer before a service may
  # reference it.
  depends_on = [aws_lb_listener.http]
}
