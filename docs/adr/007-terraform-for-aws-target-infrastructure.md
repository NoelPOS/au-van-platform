# ADR-007: Use Terraform for the AWS Target Infrastructure

## Status

Accepted

## Context

ADR-004 committed to an AWS production target — S3/CloudFront, ALB, ECS Fargate, RDS PostgreSQL, ElastiCache Redis, SQS, CloudWatch — expressed as infrastructure code and deployed only when the cost and the purpose of a deployment are both explicit. It did not say what that code is written in.

Two constraints decide the answer, and neither is about the language's expressiveness.

The first is the project's posture: no agent provisions anything. Infrastructure code is written, reviewed, and checked, and a human runs the deployment when one is actually wanted. A tool that cannot be checked without first creating real resources cannot be used under that rule.

The second is cost. ADR-004 exists because an idle demo that bills by the hour defeats the purpose of building one. A tool whose convenient defaults quietly create hourly resources moves the spending decision out of the review and into the tool.

## Options considered

- **AWS CDK.** The closest contender: the repository already builds TypeScript, so there would be one language fewer to know. Two things rule it out. `cdk bootstrap` provisions real AWS resources — an S3 bucket, a KMS key, and IAM roles — before anything can be synthesized against a real account, which is exactly the "provision first, check later" order the posture forbids. And its L2 constructs hide cost-bearing defaults: `ec2.Vpc` creates a NAT Gateway per availability zone, roughly the spend ADR-004's cost controls exist to prevent, and a reviewer reading the diff would not see it.
- **CloudFormation templates by hand.** No extra tooling and no bootstrap, but the templates are verbose enough that reviewers stop reading them, and there is no offline check that resolves references across a stack.
- **Pulumi.** Same offline-check story as Terraform, but it adds a service account and a state backend as a precondition rather than a later choice, and a second ecosystem for no gain over Terraform here.
- **A shell script over the AWS CLI.** No state, no plan, no way to tell what a re-run would change. Rejected on the first teardown-and-redeploy cycle the demo needs.
- **Terraform.** Chosen; described below.

## Decision

Terraform is the infrastructure language for AU-Van, under `infra/`, with the AWS provider version pinned and `ap-southeast-1` as the region.

`terraform validate` is the reason. It checks the full reference graph against the provider's own schema — every variable, resource attribute, and output — with no credentials, no backend, and no account. Together with `terraform fmt -check -recursive` and `terraform init -backend=false`, it gives a check that a pull request can run in CI on a runner that holds no AWS credential at all. That is the exact shape of the constraint, so the tool is chosen for it.

The code is split into root modules, each with its own state. `infra/budget/` holds the account's monthly cost budget and its notification thresholds. `infra/demo/` holds the demo topology. They are separate so that destroying the demo cannot remove the budget alarm — the guardrail has to outlive the thing it guards, or it is only present when it is not needed.

The budget module's notifications address subscriber email addresses directly rather than through an SNS topic. A topic would add a resource, a policy, and a subscription confirmation for a single recipient, and `aws_budgets_budget` takes the address itself.

Deployment stays manual. No workflow holds AWS credentials, no OIDC role is configured, and the `Infrastructure checks` CI job has no `permissions:` block and no credentials step, so it cannot reach AWS even by mistake.

The demo topology narrows ADR-004's target, deliberately and in each case for cost:

| Named in ADR-004 | Decision here |
|---|---|
| NAT Gateway and private subnets | Omitted. Roughly $32–58 per month for a demo that runs for an hour. Public subnets instead, with the task's security group accepting traffic only from the load balancer and the database's only from the task |
| RDS Multi-AZ | A variable, defaulting off. It doubles the bill for a failure mode whose answer is "restart it" |
| ElastiCache Redis | Defined, defaulted off. No application code uses Redis: ADR-003 made PostgreSQL the authority and seat holds shipped without it |
| A separate SQS worker service | Omitted. It doubles the Fargate bill to run code that does not exist |
| SQS queue and payment-proof bucket | Defined, defaulted off, once the payment and notification work has designed their interfaces |
| CloudWatch dashboard | Omitted. Three alarms are enough for one service |
| Custom domain, ACM, Route 53, WAF | Out of scope |

Everything in that table below the first row is a later issue; this decision records why the target shrank, not that the resources exist yet.

One consequence of having no custom domain is worth recording because it changes the topology rather than trimming it. Without a domain there is no ACM certificate, so the load balancer can serve only plain HTTP — and LIFF requires HTTPS, so an HTTPS page calling an HTTP origin is blocked as mixed content and the demo would not work at all. The answer costs nothing: one CloudFront distribution with two behaviours, the default serving the web build from S3 and `/api/*` pointing at the load balancer. The browser then sees a single origin, the SPA calls a relative `/api` exactly as the Vite dev proxy already gives it, and CORS stops mattering. That `/api/*` behaviour must use `CachingDisabled` with `AllViewerExceptHostHeader`. Getting it wrong is a security bug rather than a slow page: the default policy strips `Authorization` and caches the response, so one student's bookings would be served to another.

## Consequences

- A pull request can prove that infrastructure code is well-formed and that its references resolve, with no credential in CI and nothing provisioned. It cannot prove that the infrastructure works; only a deployment does that, and the pull request has to say so.
- Terraform is a language the repository did not previously use. It is the cost of an offline check; HCL for this topology is small and reviewable.
- State is local until a remote backend is chosen. That is adequate for one operator and has to change before a second one deploys.
- The budget guardrail is applied and destroyed independently of the demo. Someone has to remember to apply it first; the runbook, not the code, enforces the order.
- The scope reductions above are a demo, not a production topology. Any claim made about this deployment must say which row of that table it is standing on.
- `Infrastructure checks` is not a required status check yet. It becomes one once it has been green on real pull requests, the same way `Commit checks` was promoted.
