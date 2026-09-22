# Infrastructure

Terraform for AU-Van's AWS target, in region `ap-southeast-1`. The decision to
use Terraform, and the cost reductions this topology makes against ADR-004, are
recorded in
[ADR-007](../docs/adr/007-terraform-for-aws-target-infrastructure.md).

## Nothing here is provisioned

No AWS account holds any of this. No pipeline applies it, no workflow holds an
AWS credential, and no OIDC role exists for one to assume. Deployment is a
manual act by a person who has decided to spend the money.

**Passing checks prove that the code is well-formed and that its references
resolve against the provider schema. They do not prove that the infrastructure
works.** `terraform validate` never contacts AWS: it cannot tell you that a
subnet range is free, that a quota allows the instance, that the security
groups let the traffic you expect through, or that an `apply` would succeed.
Only a deployment shows that, and nothing in this repository performs one.

Say it the same way anywhere this work is described. "The Terraform validates"
is true; "the infrastructure is deployed" is not.

## Root modules

Each directory is a root module with its own state, initialized and applied on
its own.

| Module | What it holds |
|---|---|
| [`budget/`](budget) | The account's monthly cost budget and its notification thresholds |
| [`demo/`](demo) | The demo topology: network, registry, database, compute, load balancer, storage, IAM, monitoring |

### `budget/`

One `aws_budgets_budget`: monthly, by cost, with notifications at 50%, 80% and
100% of actual spend and at a forecast of 100%. Each notification sends to a
subscriber email address directly — there is no SNS topic, because a topic
would add a resource, a policy, and a subscription confirmation for a single
recipient.

It is a separate root module, with separate state, on purpose. Destroying the
demo must not destroy the alarm; a guardrail torn down alongside the thing it
guards is only present when it is not needed. Apply this one first and leave
it applied.

### `demo/`

The network: a VPC, two public subnets in two availability zones, an internet
gateway, a public route table, and three security groups — load balancer, task,
database — wired so the task accepts traffic only from the load balancer and
the database only from the task.

On top of it, the origin stack: an ECR repository for the API image, an RDS
PostgreSQL instance, one ECS Fargate service running that image, an application
load balancer whose target group health-checks `/actuator/health`, the private
payment-proof bucket, two IAM roles, a CloudWatch log group and three alarms.
The CloudFront distribution and the web bundle bucket are the remaining piece
and arrive in #75b.

**This module now bills by the hour.** Applied and left running, it is roughly
one Fargate task at 0.5 vCPU and 1 GB, one `db.t4g.micro` with 20 GB of gp3, one
application load balancer — the largest single line, about $16 a month — and one
Secrets Manager secret at about $0.40. Apply `budget/` first and leave it
applied; destroy this module when the demo is over. Every teardown-shaped
argument in here (`force_delete`, `force_destroy`, `skip_final_snapshot`,
`deletion_protection = false`, `backup_retention_period = 0`) is wrong for
production and deliberate for a demo that has to come down in one command.

There is no NAT gateway and there are no private subnets. A NAT gateway is
roughly $32-58 a month for a demo that runs for an hour, so the isolation comes
from the security groups instead. ADR-007 records that as the production delta.
The consequence in the topology is that the task runs with a public address —
the internet gateway is its only route to ECR, Secrets Manager, SSM, CloudWatch
Logs and the LINE API — and that the database sits in a subnet named `-public-`
with `publicly_accessible = false` and a security group that admits the task
and nothing else. The subnet's name is not the boundary; the security group is.

Two things this module does not do yet, and both are #75b's. The listener
serves plain HTTP, because without a custom domain there is no ACM certificate
— ADR-007 marks that row out of scope, and CloudFront's own `*.cloudfront.net`
certificate is what gives the browser HTTPS. And
`load_balancer_ingress_cidrs` is still open, which stopped being hypothetical
the moment there was a load balancer to reach: until #75b narrows it to the
CloudFront origin-facing prefix list, the origin is reachable directly, in
plaintext, by anyone. Do not leave this module applied and unattended in that
state.

Two rows of ADR-007's scope table are no longer right, and this module departs
from them deliberately:

- **The payment-proof bucket is created unconditionally**, where that table
  deferred it "once the payment and notification work has designed their
  interfaces". They have: ADR-009 chose the private API-brokered bucket and #51
  shipped `S3PaymentProofStorage` against it. A demo with the bucket off has a
  payment flow that 500s on the first upload, and a demo's worth of slips is
  storage-priced at cents.
- **The SQS queue is dropped entirely** rather than defined and defaulted off.
  ADR-010 designed that interface and chose a transactional outbox in PostgreSQL
  *instead of* a broker, and #61/#62/#63 shipped it, so no code path would ever
  publish to a queue. A defined-but-disabled queue would be dead HCL carrying a
  suggestion the architecture has rejected.

ElastiCache is a different case and stays defined and defaulted off: Redis is
still on the classpath and still configured, and `application.yml` disables only
its health indicator while saying to re-enable it with the first Redis-backed
feature.

### What the owner creates before the first `apply` of `demo/`

Terraform does not create these, and no agent may. ADR-012 records why.

1. **Two SecureString parameters**, whose names `terraform output
   required_ssm_parameters` prints — `/au-van/demo/jwt-secret` and
   `/au-van/demo/line-channel-access-token` by default. Create each with `aws
   ssm put-parameter --type SecureString`. The task cannot start without them,
   and that failure is deliberate: there is no placeholder that could quietly
   run in its place.
2. **The budget in `budget/`**, applied and left applied.

The database password is not on that list: RDS generates it and holds it in
Secrets Manager, and Terraform sees only an ARN.

### Two variables this module cannot supply for you

`api_image_tag` and `cors_allowed_origins` have no defaults, and neither can be
filled in correctly before the first `apply` — the ECR repository they need an
image in, and the load balancer whose DNS name the browser will use, are both
created *by* that apply. So the first deployment is two passes, and that is the
order to run them in:

1. Put a tag you intend to build in `api_image_tag` — a commit sha, not
   `latest` — and any placeholder in `cors_allowed_origins`. Apply. This creates
   the repository, the database, the load balancer and the service. **The task
   will not come up yet**: there is no image at that tag, so ECS reports a pull
   failure and the target group stays unhealthy. That is expected, not a
   misconfiguration.
2. Read `terraform output ecr_repository_url` and `terraform output
   load_balancer_dns_name`. Build the API image, tag it with the sha from step
   1, and `docker push` it to that repository.
3. Set `cors_allowed_origins` to `http://<load_balancer_dns_name>` — the origin
   the browser will actually load the app from — and apply again. The new task
   definition revision rolls the service, the task pulls the image that now
   exists, and the health check passes.

Once #75b's CloudFront distribution fronts both the web build and `/api/*`,
`cors_allowed_origins` becomes that distribution's `https://` domain, and #75b
can wire it from the resource rather than from a variable.

#76 owns the runbook that sequences all of this, and the teardown that reverses
it.

## Conventions

- Provider versions are pinned in each module's `versions.tf`.
- No account identifier, ARN containing one, email address, or secret appears
  in a committed file. Where an account id is needed, the code reads
  `data.aws_caller_identity.current.account_id`.
- Real values live in `terraform.tfvars`, which is gitignored. Each module
  ships a `terraform.tfvars.example` holding placeholders only; copy it and
  fill it in.
- `.terraform.lock.hcl` is tracked once a module has been initialized, so that
  everyone resolves the same provider build. Neither module has been
  initialized yet, so neither lock file exists; the first `terraform init` run
  by someone with Terraform installed creates one, and it should be committed.

## Checks

The `Infrastructure checks` job in `.github/workflows/ci.yml` runs, for each
root module:

```sh
terraform fmt -check -recursive infra
terraform init -backend=false -input=false
terraform validate
```

All three work with no credential, no backend and no AWS account, which is why
they are the checks this project runs. The job holds no credential and has no
`permissions:` block, so it cannot reach AWS even by mistake. It is not yet a
required status check; it becomes one after it has been green on real pull
requests, the same way `Commit checks` was promoted.

Run the same three locally if you have Terraform installed. There is no need
to install it to review a change — CI runs them on every pull request.

## Never

`terraform apply`, `terraform destroy` and every `aws` command are off limits
to agents working in this repository. Infrastructure is written and checked
here; it is deployed by a person, from a runbook, with the budget already in
place.
