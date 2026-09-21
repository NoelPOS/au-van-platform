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
| [`demo/`](demo) | The demo network: VPC, two public subnets, internet gateway, route table, security groups |

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

The network only, at this point: a VPC, two public subnets in two availability
zones, an internet gateway, a public route table, and three security groups —
load balancer, task, database — wired so the task accepts traffic only from the
load balancer and the database only from the task.

There is deliberately no compute, database, cache, bucket or CDN here yet, and
nothing in this module bills by the hour. Those arrive in a later issue.

There is also no NAT gateway and there are no private subnets. A NAT gateway
is roughly $32-58 a month for a demo that runs for an hour, so the isolation
comes from the security groups instead. ADR-007 records that as the production
delta.

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
