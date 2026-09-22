# The registry the API image is pushed to before a deployment. #31 built and
# checked that image and deliberately left the registry out of scope, because
# nothing was deployed anywhere; this is where it arrives.
#
# Only the API image. The web build is served from S3 by the CloudFront
# distribution #80 adds, so a second repository -- and the second Fargate task
# that would pull from it -- would serve bytes S3 already serves and double the
# compute bill for nothing.

resource "aws_ecr_repository" "api" {
  name = "${local.name_prefix}-api"

  # A tag names one build forever. api_image_tag has no default precisely so
  # that an operator says which build they mean, and a mutable tag would undo
  # that by letting the same name resolve to different bytes tomorrow.
  image_tag_mutability = "IMMUTABLE"

  # Wrong for production, right for a demo that has to be removable in one
  # command: a repository still holding images refuses to be destroyed, and
  # #76's teardown runbook should not need a manual image purge first.
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "${local.name_prefix}-api"
  }
}

# Untagged images are the layers a re-push of the same tag orphans. Nothing can
# deploy them -- the task definition names a tag -- so they are storage nobody
# reads.
resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after a day."

        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }

        action = {
          type = "expire"
        }
      }
    ]
  })
}
