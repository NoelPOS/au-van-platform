# AWS Target Architecture

## Two deployment modes

### Cost-conscious demo

The working demo may use low-cost or free hosting for the React application, Spring Boot API, PostgreSQL, and Redis. It must remain Dockerized and configured through environment variables so the same application can move to AWS without a code rewrite.

### Target AWS production topology

```text
Users / LINE LIFF
        |
CloudFront + S3 (React static assets)
        |
Application Load Balancer
        |
ECS Fargate service (Spring Boot API) ----> RDS PostgreSQL
        |                                      |
        +----------------------------------> ElastiCache Redis
        |
        +--> S3 (payment proof objects)
        +--> SQS (asynchronous work) --> ECS Fargate worker
        |
        +--> CloudWatch logs, metrics, alarms, health checks
```

## AWS responsibilities

| Service | Responsibility |
| --- | --- |
| S3 + CloudFront | Serve React assets and store payment-proof images |
| ALB | Terminate HTTPS and route API traffic to the service |
| ECS Fargate | Run the containerized API and independent worker |
| RDS PostgreSQL | Authoritative relational data and transactions |
| ElastiCache Redis | Temporary holds, cache, rate limits, idempotency support |
| SQS | Durable asynchronous notification, reminder, expiry, and retry work |
| CloudWatch | Logs, health checks, alarms, and operational metrics |

## Cost controls

- Do not leave ALB, NAT Gateway, RDS Multi-AZ, ECS services, or ElastiCache running solely for a portfolio demo.
- Add an AWS budget alert before any AWS deployment.
- Use infrastructure as code and documented teardown steps.
- Use the target topology for production design discussion; claim a service as deployed only after it is actually deployed.

