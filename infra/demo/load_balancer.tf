# The application load balancer in front of the API task.
#
# Port 80 and no certificate, deliberately. There is no custom domain, so there
# is no ACM certificate to attach -- the row ADR-007 marks out of scope. The
# HTTPS the browser needs comes from CloudFront's *.cloudfront.net certificate
# in #80, and the leg from CloudFront to this listener is plain HTTP. ADR-007
# and infra/README.md record that residual risk rather than paying for a domain
# to remove it.

resource "aws_lb" "main" {
  name               = "${local.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.load_balancer.id]
  subnets            = aws_subnet.public[*].id

  # Wrong for production, right for a demo that must come down in one command.
  enable_deletion_protection = false

  # A header the load balancer cannot parse is dropped rather than forwarded,
  # so a malformed request cannot be interpreted differently here and in the
  # application behind it.
  drop_invalid_header_fields = true

  tags = {
    Name = "${local.name_prefix}-alb"
  }
}

resource "aws_lb_target_group" "api" {
  name     = "${local.name_prefix}-api"
  port     = var.app_port
  protocol = "HTTP"
  vpc_id   = aws_vpc.main.id

  # Fargate tasks in awsvpc mode register by address, not by instance.
  target_type = "ip"

  # A demo deploys often and drains nothing worth waiting on; the default 300s
  # would hold a replaced task in `draining` for five minutes.
  deregistration_delay = 30

  # This is the whole of what makes the service ever reach a steady state, and
  # a path that validates but is wrong fails silently: the task runs, the target
  # never turns healthy, and ECS replaces it forever. /actuator/health is the
  # endpoint management.endpoints.web.exposure.include exposes and the one
  # api/Dockerfile's own HEALTHCHECK probes; it answers 200 when the context and
  # the datasource indicator are up and 503 when they are not, so 200 is the
  # only status that may count as healthy.
  health_check {
    enabled             = true
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${local.name_prefix}-api"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}
