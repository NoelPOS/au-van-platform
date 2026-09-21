provider "aws" {
  region = var.aws_region

  # Every resource in this module is disposable and belongs to one demo.
  # Tagging it at the provider means a cost report can attribute the bill and
  # a teardown can tell what it is allowed to remove.
  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
