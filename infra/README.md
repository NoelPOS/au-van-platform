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
| [`demo/`](demo) | The demo topology: network, registry, database, compute, load balancer, storage, CDN, IAM, monitoring |

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

In front of it, the edge: a second private bucket holding the web build, and
one CloudFront distribution over two origins — that bucket and the load
balancer. The distribution is the only thing the public talks to. The load
balancer's security group admits the CloudFront origin-facing managed prefix
list — `com.amazonaws.global.cloudfront.origin-facing` — and nothing else, so
the origin is not reachable directly.

`terraform output demo_url` is where the demo is. Its `*.cloudfront.net`
certificate is what gives the browser HTTPS with no custom domain and no ACM
certificate, which is what LIFF requires and what a plain-HTTP load balancer
could not provide. Because the same distribution serves the bundle and proxies
`/api/*`, the browser sees a single origin and the SPA calls a relative `/api`
exactly as the Vite dev proxy and `web/nginx.conf.template` already give it.

Four ordered behaviours, which are the same contract `nginx.conf.template`
implements for the container image:

| Path | Origin | Cache policy | Origin request policy | Methods |
|---|---|---|---|---|
| `/api/*` | load balancer | `Managed-CachingDisabled` | `Managed-AllViewerExceptHostHeader` | all seven |
| `/actuator/*` | load balancer | `Managed-CachingDisabled` | `Managed-AllViewerExceptHostHeader` | GET, HEAD |
| `/assets/*` | web bucket | `Managed-CachingOptimized` | — | GET, HEAD |
| default | web bucket | `Managed-CachingDisabled` | — | GET, HEAD |

The bottom two are the inverse of each other on purpose: Vite writes
content-hashed filenames under `/assets`, so those are cached for a year, and
`index.html` is never cached, or a browser keeps loading a stale document that
references asset hashes the new build no longer has. Both pairings live in the
behaviours rather than in the objects' `Cache-Control` metadata, so an upload
with the wrong `--cache-control` flag cannot reintroduce the bug. The top two
are a security control, and the next section is about them.

The distribution defines no `custom_error_response`. That argument is
distribution-wide with no per-behaviour form, so mapping 403 and 404 to
`200 /index.html` for the bucket behaviours would rewrite the API's own 403s
and 404s too, and the client would parse `index.html` where it expects a typed
error body. `nginx.conf.template` scopes its `try_files` to `location /` and
passes `^/(api|actuator)/` statuses through unchanged; having no mapping is
what matches that. `default_root_object` serves the app at `/`, and the app has
no client-side router, so nothing else needs one.

**This module now bills by the hour.** Applied and left running, it is roughly
one Fargate task at 0.5 vCPU and 1 GB, one `db.t4g.micro` with 20 GB of gp3, one
application load balancer — the largest single line, about $16 a month — one
Secrets Manager secret at about $0.40, and CloudFront, which is request- and
transfer-priced and costs a demo's worth of traffic. Apply `budget/` first and
leave it applied; destroy this module when the demo is over. Every teardown-shaped
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

### The two CloudFront policies, and why they are a security control

The `/api/*` and `/actuator/*` behaviours pair `Managed-CachingDisabled` with
`Managed-AllViewerExceptHostHeader`. ADR-007 says why, in its own words:
getting it wrong "is a security bug rather than a slow page: the default policy
strips `Authorization` and caches the response, so one student's bookings would
be served to another".

Those are two separate failures, and each policy prevents one of them.

- **`Managed-CachingDisabled`** forbids CloudFront to cache the response at
  all. A cached authenticated response is the leak: the next request for the
  same path never reaches the API, so no authorization check runs and whoever
  asks second is served whatever the first person was allowed to see.
- **`Managed-AllViewerExceptHostHeader`** forwards every viewer header, cookie
  and query string to the origin *except* `Host`, which has to stay the load
  balancer's own. `Authorization` is in "every header". The managed policies
  built for caching deliberately do not forward it — a cache key that ignores
  the credential is the whole point of them — so a request arriving at the API
  under one of those is an anonymous request, and every authenticated call
  would answer 401.

There is no automated check behind this. `terraform validate` accepts any
policy id, and `terraform test` would have to resolve data sources, which needs
an AWS credential — the one thing `Infrastructure checks` is built never to
hold. So the pairing is verified by reading `infra/demo/cdn.tf`, which states
the same reasoning directly above the two behaviours, and by the review note on
the pull request that changes it. Anyone editing those behaviours is changing a
security control, not a performance setting.

### The residual risk, accepted and not mitigated

CloudFront reaches the load balancer over plain HTTP. This follows from
ADR-007's table marking "Custom domain, ACM, Route 53, WAF" out of scope:
without a domain there is no ACM certificate, so there is nothing for the
listener to speak TLS with. Two consequences, both real:

- **The leg from CloudFront to the load balancer is plaintext.** A request
  carrying an AU-Van JWT crosses the public internet in clear text between the
  edge and the origin.
- **The prefix list admits all of CloudFront, not only this distribution.**
  There is one origin-facing prefix list and every CloudFront distribution
  makes its origin requests from inside it, so anyone who points their own
  distribution at this load balancer's DNS name is already through the
  security group.

The standard second half of an origin lock-down closes the second one: a shared
secret in a `custom_header` on the origin, the listener's default action
returning 403, and a header-conditioned rule forwarding to the target group.
**It is deliberately not built here.** It adds a listener rule, a rule
condition, and a secret with nowhere good to live — ADR-012 exists because this
repository has no place to put a secret value — to protect a demo that holds no
real data and runs for an hour. The fix that removes the risk rather than
mitigating it is a custom domain with an ACM certificate, which is the row
ADR-007 already marks out of scope.

So this is recorded, alongside the missing NAT gateway above, as a production
delta of the demo topology rather than a defect in it: **do not put real
personal data behind this deployment, and do not leave it applied when nobody
is watching a demo.** A production topology gets the domain, the certificate,
HTTPS to the origin, and this section deleted.

Three things ADR-007 says about this topology are no longer right — two rows of
its scope table and the shape of the distribution — and this module departs
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
- **The distribution has four behaviours**, where ADR-007 says "one CloudFront
  distribution with two behaviours". The extra two are not new scope: they are
  the rest of the contract `web/nginx.conf.template` already implements for the
  container image — `/actuator/*` so the health panel's request reaches the API
  instead of the bundle bucket, and `/assets/*` so the content-hashed files are
  cached while `index.html` is not. The behaviour table in the `demo/` section
  above lists all four.

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

### The one variable this module cannot supply for you

`api_image_tag` has no default, and it cannot be filled in correctly before the
first `apply` — the repository the image goes to is created *by* that apply. So
the first deployment runs in this order:

1. Put a tag you intend to build in `api_image_tag` — a commit sha, not
   `latest`. Apply. This creates the repository, the database, the load
   balancer, the service, the two buckets and the distribution. **Neither half
   of the demo works yet**: there is no image at that tag, so ECS reports a pull
   failure and the target group stays unhealthy, and the web bucket is empty, so
   the distribution has nothing to serve. Both are expected, not
   misconfigurations.
2. Read `terraform output ecr_repository_url`. Build the API image, tag it with
   the sha from step 1, and `docker push` it to that repository. ECS keeps
   relaunching the task, so it comes up on the next attempt; `aws ecs
   update-service --force-new-deployment` hurries it, because the scheduler
   backs off after repeated failures.
3. Read `terraform output web_bucket`. `npm run build` in `web/`, then `aws s3
   sync web/dist s3://<that bucket>/ --delete`. Terraform does not do this and
   deliberately holds no `aws_s3_object`: the bundle would have to exist on
   whichever machine runs `apply`, and it would live in Terraform state.
4. Open `terraform output demo_url`.

`CORS_ALLOWED_ORIGINS` is not on that list and is not a variable. The origin the
browser loads the app from is the distribution's own domain, so `compute.tf`
composes it from `aws_cloudfront_distribution.main.domain_name` — there is
nothing for an operator to get wrong and nothing to correct on a second pass.
It is not a dependency cycle: the distribution depends on the load balancer, the
task definition depends on the distribution, and neither the load balancer nor
the target group depends on the task definition.

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

`terraform apply` and `terraform destroy` are off limits to agents working in
this repository. Infrastructure is written and checked here; it is deployed by
a person, from a runbook, with the budget already in place.

`aws` commands are not denied, but they are not allowed either: each one
stops at a permission prompt and runs only if the owner approves it there.
That lets an agent read state, inspect logs and run a runbook step while the
owner watches, and nothing reaches AWS without the owner saying yes to that
exact command. Secret values are still the owner's alone. An agent never
creates, fills or reads the SSM parameters above or any other credential, with
or without a prompt; ADR-012 records why.
