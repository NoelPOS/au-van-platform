# The demo's public edge: one CloudFront distribution over two origins -- a
# private S3 bucket holding the web build, and the load balancer.
#
# This is the only thing the public is meant to talk to, and it is what makes
# the demo work at all. Without a custom domain there is no ACM certificate, so
# the load balancer can serve only plain HTTP, and LIFF requires HTTPS: an
# HTTPS page calling an HTTP origin is blocked as mixed content. CloudFront's
# own *.cloudfront.net certificate costs nothing and solves that, and because
# the same distribution serves the bundle and proxies /api/*, the browser sees
# one origin and the SPA keeps calling a relative /api exactly as the Vite dev
# proxy and web/nginx.conf.template already give it (ADR-007).

# --- The web bundle bucket --------------------------------------------------
#
# Terraform creates this bucket; it does not fill it. Uploading web/dist is an
# owner step in #76's runbook, deliberately not an aws_s3_object resource:
# that would require the build output to exist on whichever machine runs
# `apply` and would drag the bundle itself into Terraform state.

resource "aws_s3_bucket" "web" {
  # A prefix rather than a name, for the same reason as the payment-proof
  # bucket: bucket names are globally unique, so AWS appends the suffix
  # instead of an operator having to invent a free name.
  bucket_prefix = "${local.name_prefix}-web-"

  # Wrong for production, right for a demo that has to come down in one
  # command: a bucket still holding objects refuses to be destroyed, and the
  # objects here are a build output that `npm run build` reproduces.
  force_destroy = true

  tags = {
    Name = "${local.name_prefix}-web"
  }
}

# The bucket is private and stays private. The browser never addresses it: it
# addresses CloudFront, which signs its own request to S3 with the origin
# access control below. "Public bucket" is not how a web bundle is served here.
resource "aws_s3_bucket_public_access_block" "web" {
  bucket = aws_s3_bucket.web.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "web" {
  bucket = aws_s3_bucket.web.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "web" {
  bucket = aws_s3_bucket.web.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Stated rather than left to the default: a deployment replaces the whole
# bundle, so versions would only accumulate storage and give force_destroy
# more to delete.
resource "aws_s3_bucket_versioning" "web" {
  bucket = aws_s3_bucket.web.id

  versioning_configuration {
    status = "Disabled"
  }
}

# Origin access control, not the older origin access identity: OAC signs with
# SigV4, works with SSE-KMS, and is what AWS recommends for new distributions.
resource "aws_cloudfront_origin_access_control" "web" {
  name                              = "${local.name_prefix}-web"
  description                       = "Lets the demo distribution, and only it, read the web bundle bucket."
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# The bucket's only grant, and it is scoped to this distribution's ARN rather
# than to the CloudFront service as a whole. Without the condition any
# distribution in any account could read the bundle; with it, a request S3
# accepts has to have been signed by this one.
resource "aws_s3_bucket_policy" "web" {
  bucket = aws_s3_bucket.web.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowReadFromThisDistributionOnly"
        Effect    = "Allow"
        Principal = { Service = "cloudfront.amazonaws.com" }
        Action    = ["s3:GetObject"]
        Resource  = ["${aws_s3_bucket.web.arn}/*"]

        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.main.arn
          }
        }
      }
    ]
  })

  # block_public_policy rejects a policy S3 reads as public. This one is not,
  # but the two resources have no reference between them, so the ordering has
  # to be stated or an apply can race.
  depends_on = [aws_s3_bucket_public_access_block.web]
}

# --- The distribution -------------------------------------------------------

resource "aws_cloudfront_function" "spa_fallback" {
  name    = "${local.name_prefix}-spa-fallback"
  runtime = "cloudfront-js-2.0"
  comment = "Serves index.html for client-side routes on the default behaviour."
  publish = true
  code    = file("${path.module}/spa-fallback.js")
}

resource "aws_cloudfront_distribution" "main" {
  enabled         = true
  comment         = "${local.name_prefix} demo edge"
  is_ipv6_enabled = true

  # The bundle's entry point, so that the bare distribution domain serves the
  # app rather than S3's listing denial.
  default_root_object = "index.html"

  # Everywhere the demo is watched from, without paying for the edges it is
  # not. PriceClass_200 covers Asia, including Thailand; PriceClass_100 would
  # leave the audience served from North America and Europe.
  price_class = "PriceClass_200"

  origin {
    origin_id                = local.cdn_web_origin_id
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.web.id
  }

  origin {
    origin_id = local.cdn_api_origin_id

    # The load balancer's own name, which is why this distribution had to come
    # after the origin stack rather than alongside it.
    domain_name = aws_lb.main.dns_name

    # http-only because there is no certificate on the load balancer to speak
    # TLS with -- the row ADR-007 marks out of scope. infra/README.md records
    # what that leaves exposed as an accepted production delta.
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  # --- The two ALB behaviours, and the one thing to get right about them ----
  #
  # ADR-007, in its own words: that /api/* behaviour must use CachingDisabled
  # with AllViewerExceptHostHeader, and "getting it wrong is a security bug
  # rather than a slow page: the default policy strips Authorization and caches
  # the response, so one student's bookings would be served to another".
  #
  # The two policies do two different halves of that, and both are needed:
  #
  #   Managed-CachingDisabled forbids CloudFront to cache the response at all.
  #   A cached authenticated response is the leak -- the second student's
  #   request never reaches the API, so no authorization check runs.
  #
  #   Managed-AllViewerExceptHostHeader forwards every viewer header, cookie
  #   and query string to the origin except Host, which must stay the load
  #   balancer's. Authorization is in "every header": the managed policies
  #   that exist for caching deliberately do not forward it, and a request
  #   that reaches the API without it is an anonymous one -- every
  #   authenticated call would answer 401.
  #
  # Substituting a policy that merely looks similar reintroduces one half or
  # the other. #80's acceptance criterion is a reviewer reading the two
  # cache_policy_id / origin_request_policy_id pairs below.
  ordered_cache_behavior {
    path_pattern     = "/api/*"
    target_origin_id = local.cdn_api_origin_id

    # All seven methods CloudFront supports. Narrowed to GET and HEAD, every
    # booking POST would answer 405 and the demo would look dead.
    allowed_methods = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods  = ["GET", "HEAD"]

    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host_header.id

    viewer_protocol_policy = "redirect-to-https"
  }

  # The same pair, for the same reason. web/src/pages/SignInPage.tsx fetches
  # /actuator/health from the browser -- apiBaseUrl is "" unless
  # VITE_API_BASE_URL is set, so the request is same-origin -- and
  # nginx.conf.template routes ^/(api|actuator)/ for the container image. Omit
  # this behaviour and the default one answers out of the bundle bucket
  # instead: the bucket has no such object, so the landing page's health panel
  # would be reporting on the bucket and the request would never reach the API
  # at all. That is the whole of what this behaviour establishes -- that the
  # request gets to the API rather than being answered at the edge. It says
  # nothing about what the API then answers: web/src/pages/SignInPage.tsx sets
  # the panel from response.ok alone, so an API that answers 404 or 403 on
  # /actuator/health still reads as down, which is an actuator question and
  # not a routing one. Wrong in the deployed demo and nowhere else -- not
  # locally, and not in Container checks, which curls the API container
  # directly on its own network.
  ordered_cache_behavior {
    path_pattern     = "/actuator/*"
    target_origin_id = local.cdn_api_origin_id

    # Read-only. The actuator's other endpoints are not exposed, and nothing
    # in the browser posts to this path.
    allowed_methods = ["GET", "HEAD"]
    cached_methods  = ["GET", "HEAD"]

    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host_header.id

    viewer_protocol_policy = "redirect-to-https"
  }

  # --- The two bucket behaviours, which are the inverse of each other -------
  #
  # Vite writes content-hashed filenames under /assets, so a file at a given
  # name never changes and can be cached for a year. index.html must not be
  # cached at all, or a browser keeps loading a stale document that references
  # asset hashes the new build no longer has. nginx.conf.template calls getting
  # this pair backwards "the classic single-page-application deployment bug",
  # and expressing it here rather than in the objects' Cache-Control metadata
  # means an upload with the wrong --cache-control flag cannot reintroduce it.
  ordered_cache_behavior {
    path_pattern     = "/assets/*"
    target_origin_id = local.cdn_web_origin_id

    allowed_methods = ["GET", "HEAD"]
    cached_methods  = ["GET", "HEAD"]

    cache_policy_id = data.aws_cloudfront_cache_policy.caching_optimized.id

    # The only behaviour that sets this. CloudFront compresses only when the
    # behaviour asks for it and the cache policy enables gzip and Brotli;
    # Managed-CachingOptimized enables both and Managed-CachingDisabled
    # enables neither, so compress = true anywhere else would be decoration.
    compress = true

    viewer_protocol_policy = "redirect-to-https"
  }

  default_cache_behavior {
    target_origin_id = local.cdn_web_origin_id

    allowed_methods = ["GET", "HEAD"]
    cached_methods  = ["GET", "HEAD"]

    cache_policy_id = data.aws_cloudfront_cache_policy.caching_disabled.id

    viewer_protocol_policy = "redirect-to-https"

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_fallback.arn
    }
  }

  # No custom_error_response: it is distribution-wide and would turn API 403s and
  # 404s into index.html; the default behaviour's function serves client routes.
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # The *.cloudfront.net certificate. This is the whole reason the demo can
  # satisfy LIFF's HTTPS requirement with no custom domain and no ACM
  # certificate, which ADR-007 marks out of scope.
  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = {
    Name = "${local.name_prefix}-cdn"
  }
}
