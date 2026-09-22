# Two roles, and the difference between them matters.
#
# The execution role belongs to the ECS agent: it pulls the image, writes the
# log stream, and resolves the task definition's `secrets` entries before the
# container starts. The task role belongs to the application: it is what the
# AWS SDK's default credentials chain resolves inside the JVM, which is why
# .env.example sets no AWS_ACCESS_KEY_ID for the deployed target.
#
# Neither grants a kms:Decrypt statement. The RDS-managed secret and the two
# SecureString parameters both use AWS-managed keys, whose own key policies
# already allow decryption through the calling service (ADR-012).

locals {
  ecs_tasks_assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Action    = ["sts:AssumeRole"]
        Principal = { Service = "ecs-tasks.amazonaws.com" }
      }
    ]
  })
}

resource "aws_iam_role" "task_execution" {
  name               = "${local.name_prefix}-task-execution"
  description        = "Pulls the API image, writes its logs, and reads its secrets before the container starts."
  assume_role_policy = local.ecs_tasks_assume_role_policy
}

# ECR pull and CloudWatch Logs writes. The AWS-managed policy is the whole of
# what it grants, and writing those statements out by hand would only risk
# getting them wrong.
resource "aws_iam_role_policy_attachment" "task_execution_managed" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# The one thing that managed policy does not cover: reading the three values
# the task definition's `secrets` block names. Each resource is a single ARN
# rather than a prefix, so this role cannot read a parameter or a secret that
# this module did not put in front of it.
resource "aws_iam_role_policy" "task_execution_secrets" {
  name = "${local.name_prefix}-task-execution-secrets"
  role = aws_iam_role.task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadRdsManagedDatabasePassword"
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = [aws_db_instance.main.master_user_secret[0].secret_arn]
      },
      {
        Sid    = "ReadApplicationSecureStringParameters"
        Effect = "Allow"
        Action = ["ssm:GetParameters"]

        Resource = [
          "${local.ssm_parameter_arn_prefix}${local.jwt_secret_parameter_name}",
          "${local.ssm_parameter_arn_prefix}${local.line_channel_access_token_parameter_name}",
        ]
      }
    ]
  })
}

resource "aws_iam_role" "task" {
  name               = "${local.name_prefix}-task"
  description        = "The application's own identity, resolved by the AWS SDK's default credentials chain."
  assume_role_policy = local.ecs_tasks_assume_role_policy
}

# Exactly the two calls S3PaymentProofStorage makes -- putObject in store() and
# getObjectAsBytes in load() -- on exactly one bucket's objects. No bucket-level
# action: the code never lists, and a role that can list a bucket of payment
# slips can enumerate them.
resource "aws_iam_role_policy" "task_payment_proofs" {
  name = "${local.name_prefix}-task-payment-proofs"
  role = aws_iam_role.task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadAndWritePaymentProofObjects"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject"]
        Resource = ["${aws_s3_bucket.payment_proofs.arn}/*"]
      }
    ]
  })
}
