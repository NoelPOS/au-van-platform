# PostgreSQL, the source of truth for bookings, payments, seats and audit data
# (ADR-003), and the optional ElastiCache cluster nothing uses yet.
#
# The instance sits in the two subnets named `-public-`, because there is no
# other kind in this module: ADR-007 spends the NAT-gateway budget on security
# groups instead of private subnets. `publicly_accessible = false` is what
# denies it a public address, and aws_security_group.database -- ingress from
# the task's group only, no egress at all -- is the boundary that actually
# holds. A reader who sees a database in a subnet named `-public-` deserves
# both sentences rather than the subnet name alone.

resource "aws_db_subnet_group" "main" {
  name       = "${local.name_prefix}-db"
  subnet_ids = aws_subnet.public[*].id

  tags = {
    Name = "${local.name_prefix}-db"
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${local.name_prefix}-db"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "au_van"
  username = "au_van"
  port     = var.database_port

  # RDS generates the password and holds it in Secrets Manager; Terraform sees
  # only an ARN, so the password is in no tfvars file, no plan diff and no
  # state file (ADR-012). Never add a `password` argument alongside this: the
  # provider rejects the pair, and only at plan time.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  publicly_accessible    = false
  multi_az               = var.db_multi_az

  # Teardown-shaped, and every one of the three is wrong for production and
  # right for a demo that must come down in one command: no automated backups
  # to retain, no final snapshot to name, and no deletion protection to clear
  # by hand first. #76's teardown runbook depends on all three.
  backup_retention_period = 0
  skip_final_snapshot     = true
  deletion_protection     = false

  # Cost, explicitly rather than by default: Performance Insights and enhanced
  # monitoring both bill, and three CloudWatch alarms are what ADR-007 chose.
  performance_insights_enabled = false
  monitoring_interval          = 0

  # A demo has no maintenance window worth waiting for.
  apply_immediately = true

  tags = {
    Name = "${local.name_prefix}-db"
  }
}

# --- ElastiCache, defined and off -----------------------------------------
#
# Redis is on the classpath and spring.data.redis is configured, but no feature
# uses it: ADR-003 made PostgreSQL the authority, seat holds shipped without it,
# and the outbox and the expiry and waitlist sweeps are decided by a row lock
# rather than a Redis lock (ADR-010, ADR-011). So the cluster is defined and
# defaulted off, exactly as ADR-007's table says, and the task definition
# deliberately does not set SPRING_DATA_REDIS_HOST -- wiring an endpoint the
# application never dials would only make the next reader think something uses
# it.
#
# The security group lives here rather than in network.tf because it exists
# only when the cluster does.

resource "aws_elasticache_subnet_group" "main" {
  count = var.enable_redis ? 1 : 0

  name       = "${local.name_prefix}-redis"
  subnet_ids = aws_subnet.public[*].id

  tags = {
    Name = "${local.name_prefix}-redis"
  }
}

resource "aws_security_group" "redis" {
  count = var.enable_redis ? 1 : 0

  name_prefix = "${local.name_prefix}-redis-"
  description = "Redis. Reachable only from the task, and reaches nothing."
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-redis"
  }

  # name_prefix only avoids the name collision if the replacement is created
  # before the old group is destroyed.
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_task" {
  count = var.enable_redis ? 1 : 0

  security_group_id            = aws_security_group.redis[0].id
  description                  = "Redis from the application task only"
  referenced_security_group_id = aws_security_group.task.id
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
}

resource "aws_elasticache_cluster" "main" {
  count = var.enable_redis ? 1 : 0

  cluster_id           = "${local.name_prefix}-redis"
  engine               = "redis"
  node_type            = var.redis_node_type
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 6379
  subnet_group_name    = aws_elasticache_subnet_group.main[0].name
  security_group_ids   = [aws_security_group.redis[0].id]

  tags = {
    Name = "${local.name_prefix}-redis"
  }
}
