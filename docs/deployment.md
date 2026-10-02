# Deploying the AWS demo

How the owner puts the demo topology in `infra/` up for an evidence session,
checks it, records it, and takes it down again.

**Every step here belongs to the owner.** The owner runs `terraform apply` and
`terraform destroy`. An agent may run an `aws` command only when the owner
approves that exact command at the permission prompt. No agent ever creates,
fills or reads a secret value, prompt or not
([ADR-012](adr/012-deployed-secret-delivery.md)). Never paste a value from the
table below into a chat.

## Secrets contract

| Value | Lives in | Created by | What it is for |
|---|---|---|---|
| AWS sign-in | An `aws login --profile auvan` session as the IAM user `noel-admin`, with MFA, region `ap-southeast-1` | Owner | Every `terraform` and `aws` command. Never the root user, and never long-lived access keys |
| `JWT_SECRET` | SSM SecureString `/au-van/demo/jwt-secret` | Owner, before the first demo apply | Signs every AU-Van access token ([ADR-005](adr/005-line-identity-exchange-and-short-lived-jwt.md)) |
| `LINE_CHANNEL_ACCESS_TOKEN` | SSM SecureString `/au-van/demo/line-channel-access-token` | Owner, before the first demo apply | Pushes LINE notifications. A **Messaging API** channel's long-lived token, from a channel under the **same provider** as the Login channel, or every push fails with an unknown-user `404` |
| `POSTGRES_PASSWORD` | RDS-managed secret in Secrets Manager | RDS, during the demo apply | The API's database login. No person, tfvars file or Terraform state ever holds it |
| `LINE_CHANNEL_ID` | `line_channel_id` in the gitignored `infra/demo/terraform.tfvars`, passed to the task as plain environment | Owner | The **Login** channel id the API checks id tokens against. An identifier, not a secret |
| `VITE_LIFF_ID` | Build-time environment of `npm run build`, inlined into the bundle | Owner | The LIFF app id the SDK needs before any network call. Public |
| `LINE_LIFF_URL` | `line_liff_url` in `infra/demo/terraform.tfvars`, passed to the task as plain environment | Owner | `https://liff.line.me/<LIFF id>`, which the button on a notification card opens. Public. Empty sends cards without a button |
| Budget email and limit | `notification_email` and `monthly_limit_usd` in the gitignored `infra/budget/terraform.tfvars` | Owner | Where the budget notifications go, and the monthly ceiling in USD |
| Alarm email (optional) | `alarm_notification_email` in `infra/demo/terraform.tfvars` | Owner | Where the three CloudWatch alarms send. Empty creates no SNS topic |

The placeholders are documented in `.env.example`, `web/.env.example`,
`infra/demo/terraform.tfvars.example` and `infra/budget/terraform.tfvars.example`.
None of them holds a real value. `terraform output required_ssm_parameters`
prints the two parameter names from the code, so this table cannot drift from
it.

Agents do not create, fill or read any of these, including the ones that are
not secret. A loop that never holds a value cannot leak one, and the
non-secret ones still contain the owner's email address and channel
identifiers.

## Prerequisites

- A recent AWS CLI v2, one that has `aws login`.
- Terraform 1.9 or later: `brew install terraform`.
- Docker, running.
- Node 22, and `jq` (`brew install jq`).
- In the LINE Developers console, under one provider:
  - A **LINE Login** channel. Its channel id is `line_channel_id`. While the
    channel is in Developing status, only its admins and testers can sign in,
    so add the student test account as a tester.
  - A **LIFF app** on that channel with the `openid` scope. Its LIFF id is
    `VITE_LIFF_ID`. Its endpoint URL becomes the `demo_url` in deploy step 10.
  - A **Messaging API** channel. Issue its long-lived channel access token. A
    student receives a push only after adding its official account as a friend.
- The budget, applied first (deploy step 2) and never destroyed.

## Deploy

Run everything from the repository root, in one shell, on a clean checkout of
the commit you mean to deploy. The code blocks carry no `#` comments, because
an interactive zsh passes them to the command as arguments.

The cluster, service, task family, target group, database and log group names
below assume the defaults `project_name = "au-van"` and `environment = "demo"`.
They are not Terraform outputs.

1. Sign in, and check that the printed ARN ends in `user/noel-admin`.

   ```sh
   export AWS_PROFILE=auvan AWS_REGION=ap-southeast-1
   aws login --profile auvan
   aws sts get-caller-identity --query Arn --output text
   ```

   The AWS provider is pinned to 5.x (`versions.tf`) and may not read an
   `aws login` session directly. If `terraform plan` reports no valid credential
   source, export the session's temporary credentials into this shell, and
   repeat it if a later step reports an expired token:

   ```sh
   eval "$(aws configure export-credentials --profile auvan --format env)"
   ```

2. Apply the budget. Set `notification_email` and `monthly_limit_usd` first.

   ```sh
   cp infra/budget/terraform.tfvars.example infra/budget/terraform.tfvars
   terraform -chdir=infra/budget init
   terraform -chdir=infra/budget apply
   ```

   The first `init` of each module creates `.terraform.lock.hcl`; commit it
   afterwards ([infra/README.md](../infra/README.md#conventions)).

3. Create the two SecureString parameters. The JWT key is generated straight
   into the command, and the LINE token is typed into a silent prompt, so
   neither appears on screen or in shell history.

   ```sh
   aws ssm put-parameter --name /au-van/demo/jwt-secret --type SecureString \
     --value "$(openssl rand -base64 32)"

   read -rs LINE_TOKEN
   aws ssm put-parameter --name /au-van/demo/line-channel-access-token \
     --type SecureString --value "$LINE_TOKEN"
   unset LINE_TOKEN
   ```

   ADR-012 puts this before the topology. A task whose parameters are missing
   fails with a resource-initialization error naming them.

4. Write the demo tfvars.

   ```sh
   cp infra/demo/terraform.tfvars.example infra/demo/terraform.tfvars
   TAG=$(git rev-parse --short=12 HEAD)
   echo "$TAG"
   ```

   Edit `infra/demo/terraform.tfvars`:

   - `api_image_tag` = the printed `$TAG`. Never `latest`: the repository's
     tags are immutable.
   - `line_channel_id` = the Login channel id.
   - `line_liff_url` = `https://liff.line.me/<your LIFF id>`.
   - `db_multi_az = true`, for the evidence session.
   - `api_min_tasks = 2`, for the evidence session. `api_max_tasks` stays `2`.
   - `alarm_notification_email`, optionally. Confirm the subscription email
     AWS sends to it.

5. Apply the demo.

   ```sh
   terraform -chdir=infra/demo init
   terraform -chdir=infra/demo apply
   terraform -chdir=infra/demo output required_ssm_parameters
   ```

   **Neither half works yet, and that is expected.** The ECR repository is
   created by this apply, so no image exists at `$TAG`: ECS reports a pull
   failure and the target group stays unhealthy. The web bucket is empty, so the
   distribution has nothing to serve. Check that `required_ssm_parameters`
   matches the two names from step 3.

   ```sh
   REPO=$(terraform -chdir=infra/demo output -raw ecr_repository_url)
   WEB_BUCKET=$(terraform -chdir=infra/demo output -raw web_bucket)
   DEMO_URL=$(terraform -chdir=infra/demo output -raw demo_url)
   ```

6. Build and push the API image. The task definition's `runtime_platform` is
   `X86_64` (`infra/demo/compute.tf`), so build for `linux/amd64`. On Apple
   Silicon this runs under emulation and takes several minutes.

   ```sh
   aws ecr get-login-password | docker login --username AWS --password-stdin "${REPO%%/*}"
   docker build --platform linux/amd64 -t "$REPO:$TAG" api
   docker push "$REPO:$TAG"
   ```

7. Build and upload the web bundle. The empty `VITE_API_BASE_URL` and
   `VITE_E2E_AUTH` override any ignored `web/.env`, which would otherwise inline
   `http://localhost:8080` into the bundle. Both greps must print nothing.

   ```sh
   (cd web && npm ci && VITE_API_BASE_URL= VITE_E2E_AUTH= VITE_LIFF_ID=<your LIFF id> npm run build)
   grep -rl 'localhost:8080' web/dist
   grep -rl 'auvan-e2e-sign-in' web/dist
   aws s3 sync web/dist "s3://$WEB_BUCKET/" --delete
   ```

8. Restart the service now that the image exists, wait for it to settle, and
   check that the health endpoint answers `200` with `"status":"UP"` through
   CloudFront.

   ```sh
   aws ecs update-service --cluster au-van-demo-cluster --service au-van-demo-api \
     --force-new-deployment
   aws ecs wait services-stable --cluster au-van-demo-cluster --services au-van-demo-api
   curl -i "$DEMO_URL/actuator/health"
   ```

   Deployments roll: the replacement tasks start before the old ones stop, so
   there is no downtime, and the task count briefly doubles.

   If it does not settle, read
   `aws ecs describe-services --cluster au-van-demo-cluster --services au-van-demo-api --query 'services[0].events[:5]'`
   and `aws logs tail /ecs/au-van-demo/api --since 15m`.

9. Create the first administrator. Your LINE user id is under **Your user ID**
   on the Login channel's Basic settings tab.

   An ECS `run-task` override can replace a container's command and
   environment, but not its entrypoint. The API image's entrypoint is
   `java -jar /app/app.jar`, so a command override only passes arguments to the
   main application. Instead, register a one-off copy of the task definition
   whose entrypoint is Spring Boot's `PropertiesLauncher`. That launcher reads
   the main class from `LOADER_MAIN`, and the run-task override supplies it.
   The task should exit `0` and log `Administrator bootstrap completed.`

   ```sh
   BOOTSTRAP_TD=$(aws ecs register-task-definition --cli-input-json "$(
     aws ecs describe-task-definition --task-definition au-van-demo-api \
       --query taskDefinition --output json |
     jq '{family: "au-van-demo-admin-bootstrap", taskRoleArn, executionRoleArn, networkMode,
          requiresCompatibilities, cpu, memory, runtimePlatform,
          containerDefinitions: [.containerDefinitions[0] | .entryPoint =
            ["java", "-cp", "/app/app.jar", "org.springframework.boot.loader.launch.PropertiesLauncher"]]}'
   )" --query taskDefinition.taskDefinitionArn --output text)

   TASK=$(aws ecs run-task --cluster au-van-demo-cluster --launch-type FARGATE \
     --task-definition "$BOOTSTRAP_TD" \
     --network-configuration "$(aws ecs describe-services --cluster au-van-demo-cluster \
       --services au-van-demo-api --query 'services[0].networkConfiguration' --output json)" \
     --overrides '{"containerOverrides":[{"name":"api","environment":[
       {"name":"LOADER_MAIN","value":"com.auvan.api.auth.bootstrap.AdminBootstrapApplication"},
       {"name":"ADMIN_BOOTSTRAP_LINE_SUBJECT","value":"<your LINE user id>"}]}]}' \
     --query 'tasks[0].taskArn' --output text)
   aws ecs wait tasks-stopped --cluster au-van-demo-cluster --tasks "$TASK"
   aws ecs describe-tasks --cluster au-van-demo-cluster --tasks "$TASK" \
     --query 'tasks[0].containers[0].exitCode'
   aws logs tail /ecs/au-van-demo/api --since 10m | grep 'Administrator bootstrap completed'
   aws ecs deregister-task-definition --task-definition "$BOOTSTRAP_TD" --query taskDefinition.status
   ```

   It is safe to repeat, and promotes the user if one already exists. If you
   signed in before this step, reload the app so that a new token carries the
   administrator role.

10. In the LINE Developers console, set the LIFF app's endpoint URL to
    `$DEMO_URL`. Each new distribution gets a new `*.cloudfront.net` domain, so
    repeat this after every teardown and redeploy.

To deploy a later commit, build and push the new tag first, set
`api_image_tag` to it, and apply. For the web, repeat step 7.

## Smoke test

- [ ] `curl -i "$DEMO_URL/actuator/health"` answers `200` with `"status":"UP"`.
- [ ] On a phone, open `https://liff.line.me/<your LIFF id>` from LINE and sign
      in.
- [ ] As the administrator, create a route, a seat layout, a van and a
      departure under `/admin/inventory`.
- [ ] As a student (a second LINE account), book a seat on that departure.
- [ ] Upload a payment slip for the booking.
- [ ] As the administrator, approve it under `/admin/payments`.
- [ ] The student's phone receives "Your payment is approved and your booking is
      confirmed." from the official account. If it does not, check that both
      channels share a provider and that the student added the account as a
      friend.
- [ ] Open `$DEMO_URL/admin/payments` directly and reload it. The Payment review
      page loads, not an S3 `AccessDenied` document.
      `curl -s -o /dev/null -w '%{http_code} %{content_type}\n' "$DEMO_URL/admin/payments"`
      prints `200 text/html`.
- [ ] Sign in as the student in a desktop browser and copy the bearer token
      from any `/api/v1` request in the developer tools. An admin path answers
      `403` with that token and `401` with none:

      ```sh
      read -rs STUDENT_TOKEN
      curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $STUDENT_TOKEN" \
        "$DEMO_URL/api/v1/admin/access-check"
      curl -s -o /dev/null -w '%{http_code}\n' "$DEMO_URL/api/v1/admin/access-check"
      unset STUDENT_TOKEN
      ```

## Evidence for the portfolio

Before you share any of it, crop or blur the account id. It appears in the
console's account menu, in every ARN, and in `ecr_repository_url`, which the
apply summary prints. Use test LINE accounts in the recording.

- [ ] The `terraform apply` summary: `Apply complete! Resources: …` and the
      outputs.
- [ ] The ECS service with 2 running tasks in 2 AZs: the service's Tasks tab,
      and the zones from
      `aws ecs describe-tasks --cluster au-van-demo-cluster --tasks $(aws ecs list-tasks --cluster au-van-demo-cluster --service-name au-van-demo-api --query taskArns --output text) --query 'tasks[].[availabilityZone,lastStatus]' --output table`.
- [ ] The target group `au-van-demo-api` with every target `healthy`.
- [ ] The database `au-van-demo-db` with Multi-AZ: Yes.
- [ ] The distribution's Behaviors tab, showing the four behaviours.
- [ ] The three `au-van-demo-*` CloudWatch alarms and the log group
      `/ecs/au-van-demo/api` showing the Spring startup.
- [ ] The `au-van-monthly-cost` budget.
- [ ] A 2–3 minute phone recording of the smoke test's student and
      administrator journey.
- [ ] The `terraform destroy` output: `Destroy complete! Resources: …`.

## Teardown

1. Destroy the demo. CloudFront must be disabled before it can be deleted, so
   this takes a while.

   ```sh
   terraform -chdir=infra/demo destroy
   ```

   `force_delete` on the ECR repository removes its images, and `force_destroy`
   on both buckets removes their objects, so neither needs emptying first.

2. Delete the two parameters. They cost nothing, but an unused secret should
   not exist. Step 3 recreates them next time.

   ```sh
   aws ssm delete-parameters --names /au-van/demo/jwt-secret /au-van/demo/line-channel-access-token
   ```

3. Confirm that nothing billable remains. Each command should print nothing, or
   an empty list:

   ```sh
   aws rds describe-db-instances --query 'DBInstances[].DBInstanceIdentifier'
   aws elbv2 describe-load-balancers --query 'LoadBalancers[].LoadBalancerName'
   aws ecs list-clusters --query clusterArns
   aws ec2 describe-network-interfaces --query 'NetworkInterfaces[?Association.PublicIp].NetworkInterfaceId'
   aws ec2 describe-addresses --query 'Addresses[].PublicIp'
   aws cloudfront list-distributions --query 'DistributionList.Items[].[Id,Comment]'
   aws ecr describe-repositories --query 'repositories[].repositoryName'
   ```

   The RDS-managed database secret can remain in Secrets Manager, scheduled for
   deletion, during its recovery window. That is expected:
   `aws secretsmanager list-secrets --include-planned-deletion --query 'SecretList[].[Name,DeletedDate]'`.

4. Confirm that the budget survived. Do not destroy `infra/budget`. The first
   command lists `au-van-monthly-cost` and its limit, and the plan reports
   `No changes.` The console shows the same budget under Billing and Cost
   Management, then Budgets.

   ```sh
   aws budgets describe-budgets \
     --account-id "$(aws sts get-caller-identity --query Account --output text)" \
     --query 'Budgets[].[BudgetName,BudgetLimit.Amount]'
   terraform -chdir=infra/budget plan
   ```

## Cost

This is an estimate. Check it in the
[AWS Pricing Calculator](https://calculator.aws/) for `ap-southeast-1` before you
rely on it. With two tasks and a Multi-AZ database, the demo costs roughly
**$0.15–0.25 an hour**:

| Line | Rough hourly cost |
|---|---|
| Two Fargate tasks, 0.5 vCPU and 1 GB each | $0.06 |
| `db.t4g.micro` Multi-AZ, with 20 GB gp3 on each side | $0.06 |
| Application load balancer | $0.03 |
| Public IPv4 addresses (two tasks, two load-balancer nodes) | $0.02 |
| CloudFront, S3, CloudWatch, Secrets Manager, ECR | Cents for a session |

The module defaults, one task and a single-AZ database, cost roughly $0.10 an
hour. Each deployment also runs double the tasks for a few minutes. Every line
bills while the demo is up, whether anyone is using it or not, so deploy,
capture the evidence, and destroy in one sitting.

## Free always-on host

The live demo runs on an Oracle Cloud Always Free VM
([ADR-014](adr/014-free-always-on-host-with-ssh-deploys.md)), separate from the
AWS topology above. `compose.prod.yaml` overlays `compose.yaml` there: Caddy
serves `https://$PUBLIC_HOST` and is the only container that publishes ports,
and Redis is not started. Every step here belongs to the owner, and no value
from the server's `.env` ever goes into a chat, a commit or a workflow log.

### Create the VM

1. In the Oracle Cloud console, create a compute instance: image **Canonical
   Ubuntu 24.04** (aarch64), shape **VM.Standard.A1.Flex** with 2 OCPU and
   12 GB, or 4 OCPU and 24 GB (the whole Always Free allowance), a 100 GB boot
   volume, a public IPv4 address, and your own SSH public key.
2. In the VCN's security list, add ingress rules for TCP 80 and TCP 443 from
   `0.0.0.0/0`. Port 22 is open by default.
3. `PUBLIC_HOST` is the public address with dashes for dots, followed by
   `.sslip.io`: `203.0.113.7` becomes `203-0-113-7.sslip.io`. No DNS record is
   needed.

### Bootstrap

```sh
ssh ubuntu@<public address>
curl -fsSLO https://raw.githubusercontent.com/NoelPOS/au-van-platform/main/deploy/bootstrap.sh
sudo bash bootstrap.sh
```

It is safe to re-run. It installs Docker Engine and the compose plugin from
Docker's apt repository, accepts TCP 80 and 443 ahead of the image's iptables
`REJECT` rule (live and in `/etc/iptables/rules.v4`, which
`netfilter-persistent` restores at boot), adds a 4 GB swapfile, turns on
unattended upgrades, creates a `deploy` user in the `docker` group, clones the
repository to `/opt/au-van`, and installs the nightly backup in
`/etc/cron.d/au-van-backup`.

### Fill the server's `.env`

```sh
sudo -u deploy sh -c 'umask 077 && cp /opt/au-van/.env.example /opt/au-van/.env'
sudo -u deploy nano /opt/au-van/.env
```

Generate every secret on the server, not on a laptop:

- `PUBLIC_HOST`: the sslip.io name above.
- `POSTGRES_PASSWORD`: `openssl rand -base64 24`.
- `JWT_SECRET`: `openssl rand -base64 32`.
- `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`: `openssl rand -hex 16`
  each. They are the object store's root credential, not an AWS key.
- `LINE_CHANNEL_ID`, `LINE_CHANNEL_ACCESS_TOKEN`, `LINE_LIFF_URL` and
  `VITE_LIFF_ID`, as in the secrets contract above.

Leave `CORS_ALLOWED_ORIGINS` and `PAYMENT_PROOF_ENDPOINT` as they are:
`compose.prod.yaml` sets the first to `https://$PUBLIC_HOST` and `compose.yaml`
sets the second.

### Continuous deployment

`.github/workflows/deploy.yml` runs after **Continuous integration** succeeds on
a push to `main`, and from the Actions tab by hand. It SSHes to the server as
`deploy`, runs `deploy/deploy.sh` on the commit CI checked, and then requires
`https://$PUBLIC_HOST/actuator/health` to answer `"status":"UP"`.

1. On your laptop, create a key used for nothing else:
   `ssh-keygen -t ed25519 -N '' -C au-van-deploy -f au-van-deploy`.
2. On the server, append the public key to
   `/home/deploy/.ssh/authorized_keys`, on one line, bound to the deploy script:

   ```text
   command="/opt/au-van/deploy/deploy.sh",restrict ssh-ed25519 AAAA... au-van-deploy
   ```

   `restrict` turns off forwarding and terminals. The client's command, the
   commit sha, reaches `deploy.sh` as `SSH_ORIGINAL_COMMAND`, which it refuses
   unless it is a full 40-character sha.
3. On the server, print the host key line to pin:
   `awk -v h="$PUBLIC_HOST" '{print h, $1, $2}' /etc/ssh/ssh_host_ed25519_key.pub`.
   Reading it on the server, rather than with `ssh-keyscan` from elsewhere, is
   what makes the pin trustworthy.
4. In the repository settings, create an environment named `production` whose
   deployment branches are limited to `main`, and give it three secrets:
   `DEPLOY_HOST` (the sslip.io name), `DEPLOY_SSH_KEY` (the contents of the
   private key file `au-van-deploy`) and `DEPLOY_KNOWN_HOSTS` (the line from
   step 3). Then delete the private key from your laptop.

### First deploy

```sh
sudo -u deploy /opt/au-van/deploy/deploy.sh
curl -fsS "https://$PUBLIC_HOST/actuator/health"
```

`deploy.sh` refuses to run without `/opt/au-van/.env`. It fetches `main`,
checks out its tip (or the sha it is given), rebuilds and restarts the stack,
waits until every container reports healthy, and prunes dangling images. Caddy
obtains the certificate when it first starts, so the first `curl` may fail for a
few seconds.

Then:

1. Create the first administrator with your LINE user id, as in deploy step 9
   above. The process should exit `0` and log
   `Administrator bootstrap completed.`

   ```sh
   cd /opt/au-van
   sudo -u deploy docker compose -f compose.yaml -f compose.prod.yaml run --rm --no-deps \
     -e LOADER_MAIN=com.auvan.api.auth.bootstrap.AdminBootstrapApplication \
     -e ADMIN_BOOTSTRAP_LINE_SUBJECT='<your LINE user id>' \
     --entrypoint 'java -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher' api
   ```

2. In the LINE Developers console, set the LIFF app's endpoint URL to
   `https://$PUBLIC_HOST`. It changes only if the VM's public address does.
3. Run the smoke test above against `https://$PUBLIC_HOST`.

### Rollback

Deploy an earlier commit of `main` by its full sha:

```sh
cd /opt/au-van
sudo -u deploy git log --oneline -10 origin/main
sudo -u deploy deploy/deploy.sh "$(sudo -u deploy git rev-parse <short sha>)"
```

The next push to `main` deploys its tip again. A rollback does not undo a
database migration; restore a backup for that.

### Backups

At 03:00 server time, `deploy/backup.sh` writes a `pg_dump` in custom format to
`/opt/au-van/backups/` and keeps the newest seven. It logs to syslog as
`au-van-backup`. The dumps share the VM's disk and only `deploy` can read
them, so copy one off the server before any change you might want to undo:

```sh
ssh ubuntu@<public address> sudo cat /opt/au-van/backups/<file>.dump > <file>.dump
```

To restore one, as `deploy` in `/opt/au-van`, with the API stopped so nothing
writes during the restore:

```sh
docker compose -f compose.yaml -f compose.prod.yaml stop api
docker compose -f compose.yaml -f compose.prod.yaml exec -T postgres \
  sh -c 'pg_restore --clean --if-exists -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < backups/<file>.dump
docker compose -f compose.yaml -f compose.prod.yaml start api
```
