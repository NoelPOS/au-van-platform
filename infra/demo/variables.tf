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
  description = "Ranges allowed to reach the load balancer on port 80. Narrow this to your own address to demo privately."
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
