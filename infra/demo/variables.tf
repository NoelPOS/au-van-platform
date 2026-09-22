variable "aws_region" {
  description = "Region the demo is deployed to."
  type        = string
  default     = "ap-southeast-1"
}

variable "project_name" {
  description = "Name prefix and Project tag for every resource."
  type        = string
  default     = "au-van"
}

variable "environment" {
  description = "Environment name, used in the name prefix and the Environment tag."
  type        = string
  default     = "demo"
}

variable "vpc_cidr" {
  description = "Address range of the demo VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "Address ranges of the public subnets, one per availability zone. Exactly two, because a load balancer needs two."
  type        = list(string)
  default     = ["10.20.0.0/24", "10.20.1.0/24"]

  validation {
    condition     = length(var.public_subnet_cidrs) == 2
    error_message = "public_subnet_cidrs must hold exactly two ranges, one per availability zone."
  }
}

variable "load_balancer_ingress_cidrs" {
  description = "Ranges allowed to reach the load balancer on port 80. Open by default, which stopped being hypothetical when #75a added the load balancer and the service behind it. ADR-007 puts CloudFront in front of that load balancer forwarding Authorization, so an open range leaves the origin reachable directly in plaintext, bypassing CloudFront while carrying a JWT. #75b narrows it, and until it lands this is the module's known exposure. Note that the right narrowing is the com.amazonaws.global.cloudfront.origin-facing managed prefix list, which this variable cannot express: aws_vpc_security_group_ingress_rule takes prefix_list_id as a mutually exclusive alternative to cidr_ipv4, so #75b needs its own variable and its own rule resource rather than a value here, and sets this default to []."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "app_port" {
  description = "Port the API container listens on, and the only port the load balancer may reach it over."
  type        = number
  default     = 8080
}

variable "database_port" {
  description = "Port the PostgreSQL instance listens on, and the only port the task may reach it over."
  type        = number
  default     = 5432
}

variable "api_image_tag" {
  description = "Tag of the API image in this module's ECR repository. Deliberately has no default: an operator must name the build they mean, and a floating tag would let two applies deploy different code with no diff between them."
  type        = string
}

variable "db_instance_class" {
  description = "RDS instance class. The smallest Graviton class, per ADR-004's cost posture."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Size of the RDS volume in GB. 20 is the gp3 minimum, and a demo's bookings are kilobytes."
  type        = number
  default     = 20
}

variable "db_engine_version" {
  description = "PostgreSQL version. Major-version only, so RDS picks the current minor; 17 matches the postgres:17-alpine image compose.yaml runs locally and in CI."
  type        = string
  default     = "17"
}

variable "db_multi_az" {
  description = "Whether the database runs in two availability zones. Off by default: ADR-007 records that it doubles the bill for a failure mode whose demo answer is to restart."
  type        = bool
  default     = false
}

variable "enable_redis" {
  description = "Whether to create the ElastiCache cluster. Off by default: Redis is on the classpath and configured, but no feature uses it yet (ADR-003, and application.yml's note next to management.health.redis.enabled)."
  type        = bool
  default     = false
}

variable "redis_node_type" {
  description = "ElastiCache node type, used only when enable_redis is true."
  type        = string
  default     = "cache.t4g.micro"
}

variable "task_cpu" {
  description = "Fargate CPU units for the API task. 512 is half a vCPU."
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Fargate memory for the API task, in MiB. Not the 512 api/Dockerfile:68-69 uses: that comment explains MaxRAMPercentage=75 by way of a 512 MiB task and is not a sizing recommendation. 75% of 512 MiB is a 384 MiB heap with metaspace, code cache and thread stacks still to come out of the same limit."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "How many API tasks the service runs. More than one is safe by design -- Flyway takes its own lock, and the expiry, waitlist and outbox sweeps are decided by a row lock and a conditional-update claim (ADR-010, ADR-011) -- but a demo does not need two."
  type        = number
  default     = 1
}

variable "line_channel_id" {
  description = "LINE Login channel id the API validates id tokens against. An audience identifier rather than a credential, in the same category as the LIFF id web/.env.example already treats as public. Empty leaves the LIFF exchange unconfigured."
  type        = string
  default     = ""
}

variable "cors_allowed_origins" {
  description = "Origins the API's CORS filter and the actuator's accept, comma-separated. Deliberately has no default, for the same reason api_image_tag has none: only the operator knows the origin the browser will load the app from, and there is no value that is right before they say. Empty is not that value and same-origin is not an exemption -- per the Fetch spec a browser sends an Origin header on every request whose method is not GET or HEAD, including a same-origin one, and Spring's CorsUtils.isCorsRequest keys on that header being present rather than on it differing. An empty string binds as an empty list, so DefaultCorsProcessor would answer 403 Invalid CORS request to every POST, PUT and DELETE -- login, booking creation, payment-proof upload -- before authentication runs, while GETs kept working. Set it to the browser's origin: http://<the load_balancer_dns_name output> while this module is all there is, and https://<distribution domain> once #75b's CloudFront distribution fronts both the web build and /api/*. Never a wildcard -- these requests carry an Authorization header."
  type        = string
}

variable "alarm_notification_email" {
  description = "Address the CloudWatch alarms notify. Empty by default, which creates no SNS topic and no subscription: an alarm with no action still shows its state, and a topic is created only when somebody has asked to be told."
  type        = string
  default     = ""
}

variable "log_retention_days" {
  description = "How long the API's CloudWatch log group keeps events. A week is longer than any demo and keeps the storage line at cents."
  type        = number
  default     = 7
}

variable "ssm_parameter_prefix" {
  description = "Path prefix of the two SecureString parameters the owner creates by hand before the first deployment (ADR-012). Terraform neither creates nor reads them; it composes the ARNs the execution role may read."
  type        = string
  default     = "/au-van/demo"

  validation {
    condition     = startswith(var.ssm_parameter_prefix, "/") && !endswith(var.ssm_parameter_prefix, "/")
    error_message = "ssm_parameter_prefix must start with a slash and must not end with one."
  }
}
