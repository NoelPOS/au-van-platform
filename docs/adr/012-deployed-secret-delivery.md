# ADR-012: Deliver Deployed Secrets Through Parameter Store and an RDS-Managed Password

## Status

Proposed

## Context

ADR-007 puts the AWS target in Terraform and keeps deployment manual: no
workflow holds an AWS credential, no OIDC role exists, and an agent never
applies. It does not say how a secret reaches the running container, and the
demo topology cannot be written without answering that.

The API needs five runtime values that are not public. `POSTGRES_PASSWORD` has
no default and the application will not start without it
(`api/src/main/resources/application.yml:7`). `JWT_SECRET` is the signing key
for every AU-Van token (ADR-005). `LINE_CHANNEL_ACCESS_TOKEN` is a Messaging
API channel credential. `LINE_CHANNEL_ID` is a Login channel identifier, and
`PAYMENT_PROOF_BUCKET` and the rest are ordinary configuration.

Two constraints, both already established in this repository. Nothing in
`AGENTS.md`'s quality rules may commit a credential. And the agent loop must
never create, fill, or read one: #76 exists to say that in the runbook, and
this decision has to make it structurally true rather than a rule someone
follows.

There is a third constraint that is easy to miss. Terraform state holds the
value of anything Terraform creates or reads, including a `sensitive` variable
and including a data source's result. "Marked sensitive" hides a value from the
plan output; it does not keep it out of state. State is local for now
(ADR-007), which means a secret in state is a secret in a file on the owner's
laptop with no encryption and no lifecycle.

## Options considered

- **Plain `environment` entries in the task definition, values from a gitignored
  `terraform.tfvars`.** Simplest and the repository's usual instinct toward the
  boring option, but every value lands in three places at once: the tfvars file
  the owner edits by hand, Terraform state, and the task definition itself,
  where `ecs:DescribeTaskDefinition` reveals it to anyone with read access to
  the account. Rejected: the signing key for every token in the system should
  not be readable from a console list view.
- **Terraform creates Secrets Manager secrets from `sensitive` variables.**
  Moves the value out of the task definition but not out of tfvars or state, and
  adds about $0.40 per secret per month. It pays money to solve one of the three
  exposures. Rejected.
- **Terraform creates SSM `SecureString` parameters with placeholder values and
  `lifecycle { ignore_changes = [value] }`, and the owner overwrites them.**
  Keeps the real value out of state and out of tfvars, and the shape is complete
  after `apply`. Rejected for its failure mode: if the owner forgets the
  overwrite, the service starts successfully and signs real tokens with a
  placeholder that is committed in the repository. A security control whose
  failure is silent and whose failure state looks healthy is worse than no
  control.
- **`data "aws_ssm_parameter"` to look the values up.** A data source reads the
  value into state, which is the exposure being avoided, and the task definition
  needs only the ARN. Rejected for spending the exposure on nothing.
- **The application fetches its own secrets from Parameter Store at startup.**
  Requires application code, an AWS SDK dependency in the auth path, and a
  second configuration mechanism next to Spring's. The application already reads
  environment variables and that contract is exercised by `compose.yaml` and by
  `Container checks`. Rejected as a change to the wrong layer.
- **An RDS-managed master password for the database credential.** Chosen for
  that one value; described below.

## Decision

Secrets reach the container through the ECS task definition's `secrets` block,
and Terraform never holds a secret value.

**The database password is managed by RDS.** `aws_db_instance` sets
`manage_master_user_password = true`; RDS generates the password, stores it in
Secrets Manager, and Terraform sees only an ARN. The task definition maps it to
the environment variable the application expects:

```hcl
secrets = [{
  name      = "POSTGRES_PASSWORD"
  valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::"
}]
```

No human, no tfvars file, and no state file ever contains that password.

**The application secrets are SSM Parameter Store `SecureString` parameters that
Terraform neither creates nor reads.** `JWT_SECRET` and
`LINE_CHANNEL_ACCESS_TOKEN` live at `/au-van/demo/jwt-secret` and
`/au-van/demo/line-channel-access-token`. The task definition references them by
an ARN composed from the region, `data.aws_caller_identity.current.account_id`
and the parameter name — a string, not a lookup — and the execution role is
granted `ssm:GetParameters` on exactly those two ARNs and nothing else. The
owner creates both with `aws ssm put-parameter --type SecureString` before the
first deployment; #76's runbook owns that step, and `terraform output` names the
two parameters so the runbook cannot drift from the code.

**Everything that is not a credential stays in `environment`.** The datasource
URL and username, `CORS_ALLOWED_ORIGINS`, `PAYMENT_PROOF_BUCKET`,
`PAYMENT_PROOF_REGION`, and `LINE_CHANNEL_ID` — a Login channel's audience
identifier, not a secret, in the same category as the LIFF id that
`web/.env.example` already treats as public.

**No AWS access key is set for the task.** `PAYMENT_PROOF_ENDPOINT` stays
unset so the S3 client addresses real S3, and the SDK's default credentials
chain resolves the task role, which carries `s3:PutObject` and `s3:GetObject`
on the payment-proof bucket and nothing else. `.env.example:34-36` already
describes this as the deployed target's behaviour.

## Consequences

- No secret value exists in any committed file, in any `terraform.tfvars`, in
  Terraform state, or in a task definition that `DescribeTaskDefinition`
  returns. The agent loop cannot leak one because it never has one.
- A deployment whose parameters have not been created fails loudly and early:
  ECS cannot start the task and reports a resource-initialization error naming
  the missing parameter. That is the intended failure. There is deliberately no
  placeholder that could run in its place.
- The apply order is now part of the contract and not only a convenience: budget
  guardrail, then parameters, then the topology. #76's runbook owns it, and this
  ADR is the reason the middle step exists.
- Parameter Store standard `SecureString` parameters cost nothing. The
  RDS-managed secret is one Secrets Manager secret at about $0.40 a month, and
  it survives teardown for its recovery window; the teardown runbook should say
  so rather than let it surprise the next bill.
- Rotating `JWT_SECRET` is `aws ssm put-parameter --overwrite` followed by a
  service redeployment, and it invalidates every token in flight — a 15-minute
  window per `auth.jwt.access-token-ttl`. No Terraform change is involved,
  which is the right shape: rotating a secret is an operation, not a code change.
- Reading the parameter names out of the task definition tells an operator what
  the system needs without telling them what the values are, which is what makes
  #76's secrets contract documentable at all.
- This covers the demo. A second environment, or a second operator, needs a
  remote state backend first (ADR-007's existing consequence) and should revisit
  whether rotation should be automatic rather than manual.
