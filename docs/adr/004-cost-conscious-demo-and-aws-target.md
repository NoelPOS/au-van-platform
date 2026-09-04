# ADR-004: Use a Cost-Conscious Demo with an AWS-Ready Production Target

## Status

Accepted

## Context

AU-Van should demonstrate cloud deployment knowledge without continuously operating expensive production infrastructure during development.

## Options considered

- Run the full production AWS topology throughout development.
- Use only non-AWS hosting and omit production infrastructure design.
- Run a low-cost demo while maintaining tested, infrastructure-as-code-ready AWS production design.

## Decision

Use low-cost or free hosting for the early demo. Keep the application Dockerized and configure the target AWS topology in infrastructure documentation and code: S3/CloudFront, ALB, ECS Fargate, RDS PostgreSQL, ElastiCache Redis, SQS, and CloudWatch. Deploy AWS services only when their cost and verification purpose are explicit.

## Consequences

- The project can be demonstrated without ongoing AWS infrastructure expense.
- Production claims in the resume and README must distinguish planned design from deployed services.
- Every future AWS deployment requires a budget alert and teardown/runbook.

