# ADR-015: Run the Live Demo on an AWS EC2 Instance Paid from Credits

## Status

Accepted. Supersedes [ADR-014](014-free-always-on-host-with-ssh-deploys.md)'s choice of host; the rest of ADR-014 stands.

## Context

ADR-014 put the always-on live demo on an Oracle Cloud Always Free VM. Oracle's sign-up did not accept any card the owner has, so no Oracle account could be opened. The project still needs the same thing ADR-014 describes: an HTTPS URL that is up every day, running the whole `compose.yaml` stack with its sweeps awake.

The owner's AWS account holds promotional credits.

## Options considered

- **Oracle Cloud Always Free.** Unavailable without an accepted card.
- **A free platform tier that sleeps, or a serverless PostgreSQL free tier.** Rejected in ADR-014 because the sweeps of ADR-010 and ADR-011 stop while the service sleeps. Nothing about that has changed.
- **The `infra/demo` topology, left running.** Fargate, a load balancer and RDS cost roughly $0.10 an hour at the module defaults, around $73 a month, several times the price of one small instance. Rejected.
- **One small EC2 instance running the same compose stack, paid from the credits.** Chosen.

## Decision

The live demo runs on one `t4g.small` instance (arm64, 2 GB) in `ap-southeast-1`, from the Ubuntu 24.04 arm64 image, with a 20 GB encrypted gp3 root volume, IMDSv2 required, an Elastic IP, and a security group that admits TCP 22, 80 and 443. The owner created it with the AWS CLI on 2026-10-04; it is not managed by Terraform.

Everything else in ADR-014's decision carries over unchanged: Caddy as the one origin on a free host name (a DuckDNS subdomain, where ADR-014 used sslip.io), no Redis, deploys pushed over SSH from a GitHub-hosted runner to a forced-command key in a `production` environment limited to `main`, and every image published for linux/arm64.

The server pulls the repository over SSH with a read-only GitHub deploy key, although the repository is public, so that a deploy keeps working if it is made private again.

## Consequences

- The demo is no longer free. It costs about $17 a month, drawn from the AWS credits for now, which departs from ADR-004's aim of no ongoing AWS expense for the demo.
- The Elastic IP keeps the address stable across a stop and start, so the DuckDNS record needs no updater.
- Because the instance is outside Terraform, `terraform destroy` of the demo topology never touches it, and its configuration is recorded in `docs/deployment.md` rather than in code.
- With 2 GB of memory, the Gradle and Vite builds at each deploy lean on the 4 GB swapfile `deploy/bootstrap.sh` creates, so a deploy is slow and the running stack competes with it.
- ADR-014's other consequences still hold: one instance is a single point of failure, backups share its disk, and the `deploy` user is root-equivalent.
- Oracle's reclamation of idle Always Free instances no longer applies. `deploy/bootstrap.sh` keeps its iptables step for Oracle-style images; on EC2 it is harmless.
