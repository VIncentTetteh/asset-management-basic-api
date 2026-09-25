# Disaster recovery — AssetIQ staging (AWS, eu-central-1)

Owner: platform · Last rehearsed: see [Rehearsal log](#rehearsal-log)

An untested backup is not a backup. The acceptance test for this document is not
that it exists, but that the restore in [Scenario 1](#scenario-1-database-lost-or-corrupted)
has been executed end to end and the date recorded below.

## Objectives

| Metric | Target | What actually determines it |
|---|---|---|
| **RPO** (data loss) | ≤ 5 minutes | RDS continuous backup to S3. PITR granularity is ~5 minutes, so a restore lands within that of the failure. |
| **RTO** (time to serve) | ≤ 90 minutes | Dominated by RDS restore time for a 20 GB instance (~20–40 min), plus stack redeploy and DNS/CloudFront propagation. |
| **Backup retention** | **1 day** | Forced down from the template default of 7. The AWS account hosting this is on the credit-based **FREE plan**, which rejects longer retention outright (`The specified backup retention period exceeds the maximum available to free tier customers`). **This is the weakest link in the whole DR story**: anything noticed more than 24 hours after it happened is unrecoverable. Raising it requires upgrading the account plan, not editing the template. |

These are staging targets. Production needs Multi-AZ (removes the restore from the
critical path for an AZ failure), a longer retention window, and cross-region
snapshot copies — none of which are configured here.

The retention limit is not a cost trade-off that was chosen; it is imposed by the
account plan. A FREE-plan account also carries a credit balance that expires, after
which the environment stops rather than bills. Treat this account as a sandbox with
a deadline, not as somewhere durable data should live.

## What is protected, and what is not

| Asset | Mechanism | Notes |
|---|---|---|
| Postgres data | RDS automated backups + PITR, encrypted with the stack's KMS key | The only irreplaceable state. |
| Generated reports and imports | S3 `assetiq-staging-s3-appdata-*`, versioned, 30-day noncurrent expiry | Recreatable from source data; versioning covers accidental deletion. |
| Secrets | Secrets Manager, 30-day recovery window, KMS-encrypted | Deleting the KMS key is unrecoverable — hence `DeletionPolicy: Retain`. |
| Container images | ECR, last 10 retained, immutable tags | A tag always identifies exactly one build. |
| Frontend build | Rebuildable from git; S3 bucket is not versioned | Losing it costs one `npm run build` and one sync. |
| **Application instance** | **Not protected. Deliberately disposable.** | Holds no state: Redis is a cache, files go to S3, config comes from Secrets Manager at boot. Replace it, do not repair it. |

## Scenario 1: database lost or corrupted

The restore path, and the one to rehearse.

```bash
export AWS_REGION=eu-central-1
STACK=assetiq-staging-foundation
SRC=assetiq-staging-rds-main

# 1. Confirm the window you can restore into. LatestRestorableTime is the real RPO.
aws rds describe-db-instances --db-instance-identifier "$SRC" \
  --query 'DBInstances[0].{Earliest:EarliestRestorableTime,Latest:LatestRestorableTime}'

# 2. Restore to a NEW instance. Never restore over the source: if the target time
#    is wrong you have then destroyed the only copy that could tell you so.
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier "$SRC" \
  --target-db-instance-identifier "${SRC}-restore" \
  --restore-time 2026-08-12T09:00:00Z \
  --db-subnet-group-name "$(aws cloudformation describe-stack-resource \
       --stack-name $STACK --logical-resource-id DbSubnetGroup \
       --query 'StackResourceDetail.PhysicalResourceId' --output text)" \
  --vpc-security-group-ids "$(aws cloudformation describe-stack-resource \
       --stack-name $STACK --logical-resource-id DatabaseSecurityGroup \
       --query 'StackResourceDetail.PhysicalResourceId' --output text)" \
  --no-publicly-accessible --db-instance-class db.t4g.micro

# 3. Wait, then verify the restore holds real data BEFORE cutting over.
aws rds wait db-instance-available --db-instance-identifier "${SRC}-restore"
```

Verify from the application instance (it already holds the DB credentials and is
inside the security group; see [Getting a shell](#getting-a-shell)):

```bash
psql "$SPRING_DATASOURCE_URL" -c "SELECT count(*) FROM organisation;"
psql "$SPRING_DATASOURCE_URL" -c "SELECT max(created_at) FROM audit_event;"
```

`max(created_at)` on `audit_event` is the sharpest check available: it is written
on every API request, so it dates the restore to the minute and tells you
immediately whether you picked the right `--restore-time`.

Cut over by pointing the application at the restored endpoint and replacing the
instance:

```bash
aws cloudformation deploy --template-file infra/aws/compute-cdn.yaml \
  --stack-name assetiq-staging-compute --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides ImageTag=<tag> SubnetId=<subnet>
```

> **Restoring does not rotate credentials.** The restored instance keeps the
> source's master user secret. If the incident involved credential compromise,
> rotate in Secrets Manager before the cutover, not after.

## Scenario 2: application instance lost

No data is at risk. Delete and redeploy the compute stack; user data re-fetches
secrets, pulls the pinned image, and restarts. The Elastic IP is retained by the
stack, so the CloudFront origin address does not change and no distribution
update is needed.

Expected recovery: ~10 minutes, most of it the instance's first boot and the JVM
starting.

## Scenario 3: region unavailable

**Not covered.** There is no cross-region replica, no cross-region snapshot copy,
and the S3 buckets are single-region. Recovery would mean rebuilding from the
templates in another region against an empty database.

This is an accepted staging risk and an explicit gap for production. Closing it
means enabling cross-region automated backup replication and copying the KMS key
material policy to the target region.

## Scenario 4: accidental stack deletion

The blast radius is deliberately bounded by policy, not by care:

- `Database` — `DeletionPolicy: Snapshot` **and** `DeletionProtection: true`, so a
  delete fails outright rather than proceeding.
- `SecretsKey`, `AppStorageBucket` — `DeletionPolicy: Retain`.
- Everything in `compute-cdn.yaml` is disposable by design.

Deleting the foundation stack therefore cannot destroy the database or make its
backups unreadable, which is the failure this arrangement exists to prevent.

## Getting a shell

There is no SSH port and no key pair. Access is Session Manager, which requires
no inbound rule and records every session in CloudTrail:

```bash
aws ssm start-session --target <instance-id> --region eu-central-1
sudo docker logs --tail 200 assetiq
sudo docker inspect assetiq --format '{{.State.Health.Status}}{{.State.Status}}'
```

`/etc/assetiq.env` on the instance holds the running configuration including
resolved secrets, mode `600`. Do not cat it into a shared terminal.

## Rehearsal log

A restore that has not been performed is a hypothesis.

| Date | Scenario | Result | RTO achieved | By |
|---|---|---|---|---|
| 2026-08-12 | 1 (PITR restore) | See note below | — | initial deployment |

**2026-08-12 note.** The stack was created on this date, so no meaningful restore
point existed yet: RDS needs a completed first backup window before
`EarliestRestorableTime` is populated. The rehearsal is therefore **outstanding**
and must be run once the first automated backup completes (backup window
02:00–03:00 UTC). Until that row is filled in with a real result, treat the RPO
and RTO figures above as design intent rather than measured fact.
