# The demo network: a VPC, two public subnets, an internet gateway, one route
# table, and the three security groups the topology will hang off.
#
# There are no private subnets and no NAT gateway. A NAT gateway is roughly
# $32-58 a month for a demo that runs for an hour, so ADR-007 spends the
# isolation budget on security groups instead: the task accepts traffic only
# from the load balancer, and the database only from the task. The production
# delta is recorded in that ADR rather than paid for here.

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${local.name_prefix}-vpc"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-igw"
  }
}

resource "aws_subnet" "public" {
  count = length(var.public_subnet_cidrs)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = local.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.name_prefix}-public-${local.availability_zones[count.index]}"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-public"
  }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# --- Security groups -------------------------------------------------------
#
# Rules are separate resources rather than inline blocks. The load balancer
# egresses to the task and the task ingresses from the load balancer, which as
# inline blocks would make the two groups depend on each other and Terraform
# would refuse the cycle.

resource "aws_security_group" "load_balancer" {
  name        = "${local.name_prefix}-alb"
  description = "Public entry point. The only group reachable from the internet."
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-alb"
  }
}

resource "aws_vpc_security_group_ingress_rule" "load_balancer_http" {
  for_each = toset(var.load_balancer_ingress_cidrs)

  security_group_id = aws_security_group.load_balancer.id
  description       = "HTTP from ${each.value}"
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_egress_rule" "load_balancer_to_task" {
  security_group_id            = aws_security_group.load_balancer.id
  description                  = "Forward requests and health checks to the task"
  referenced_security_group_id = aws_security_group.task.id
  ip_protocol                  = "tcp"
  from_port                    = var.app_port
  to_port                      = var.app_port
}

resource "aws_security_group" "task" {
  name        = "${local.name_prefix}-task"
  description = "Application task. Reachable only from the load balancer."
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-task"
  }
}

resource "aws_vpc_security_group_ingress_rule" "task_from_load_balancer" {
  security_group_id            = aws_security_group.task.id
  description                  = "Application traffic from the load balancer only"
  referenced_security_group_id = aws_security_group.load_balancer.id
  ip_protocol                  = "tcp"
  from_port                    = var.app_port
  to_port                      = var.app_port
}

# The task sits in a public subnet with no NAT gateway, so this is how it
# pulls its image, reads secrets, writes logs, and calls the LINE API.
resource "aws_vpc_security_group_egress_rule" "task_outbound" {
  security_group_id = aws_security_group.task.id
  description       = "Outbound to AWS APIs, the image registry, and LINE"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# No egress rule, deliberately: a database that cannot start a connection of
# its own is one fewer path out of the network.
resource "aws_security_group" "database" {
  name        = "${local.name_prefix}-db"
  description = "PostgreSQL. Reachable only from the task, and reaches nothing."
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-db"
  }
}

resource "aws_vpc_security_group_ingress_rule" "database_from_task" {
  security_group_id            = aws_security_group.database.id
  description                  = "PostgreSQL from the application task only"
  referenced_security_group_id = aws_security_group.task.id
  ip_protocol                  = "tcp"
  from_port                    = var.database_port
  to_port                      = var.database_port
}
