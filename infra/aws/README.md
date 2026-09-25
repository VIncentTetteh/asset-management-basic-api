# AssetIQ on AWS — staging

Two CloudFormation stacks, deployed with the AWS CLI. Region **eu-central-1**,
account **369736283466** (`techieszon`), using the `assetiq` CLI profile.

> **The account is on AWS's credit-based FREE plan.** It caps RDS backup retention
> (1 day, not the template default of 7) and carries an expiring credit balance,
> after which the environment is restricted rather than billed. Deployments use the
> `assetiq-deployer` IAM user rather than root, because root sessions here expire
> within minutes and root credentials cannot be scoped.

## Shape

```
                    ┌───────────────────────────────┐
   browser ──HTTPS──▶      CloudFront distribution   │
                    │                                │
                    │  /*      ──▶ S3 (web, private, │
                    │                  OAC + index   │
                    │                  rewrite fn)   │
                    │                                │
                    │  /api/*  ──▶ EC2 :8080  ───────┼──▶ RDS Postgres (private)
                    │  /actuator/* ┘   (Elastic IP)  │    Redis (container, local)
                    └───────────────────────────────┘    S3 (app data)
```

**One distribution serves both, and that is load bearing rather than tidy.** The
session is an `HttpOnly; SameSite=Strict` cookie, so a browser will not send it to
an API on a different site — splitting the frontend and API across two hostnames
breaks login outright. Same-origin also removes CORS preflights and avoids the
mixed-content block that an HTTPS page calling a plain-HTTP origin would hit.

It is also why `src/lib/axios.ts` in the web app still uses `baseURL: "/api/v1"`
with no environment variable: CloudFront now performs the routing the deleted Next
proxy route used to.

## Stacks

| Stack | Template | Contains | Replaceable? |
|---|---|---|---|
| `assetiq-staging-foundation` | `foundation.yaml` | KMS key, secrets, ECR, S3 buckets, RDS, security groups, instance role | **No.** Holds all state. Deletion is blocked by policy. |
| `assetiq-staging-compute` | `compute-cdn.yaml` | Elastic IP, EC2 instance, CloudFront, edge functions, bucket policy | Yes. Delete and redeploy freely. |

The split exists so the API can be redeployed, resized, or rebuilt without the
database ever being in the blast radius.

## Deploying

Prerequisites: AWS CLI v2 authenticated, Docker running.

### 1. Foundation (first time, or when storage/secrets/database change)

```bash
export AWS_REGION=eu-central-1
aws cloudformation deploy \
  --template-file infra/aws/foundation.yaml \
  --stack-name assetiq-staging-foundation \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
      VpcId=vpc-0523a9f29c2982fe9 \
      "SubnetIds=subnet-06a1256f7cc04c2bd,subnet-0fe3e6d16c56071b5,subnet-054e8cf4d8babce17" \
      DbBackupRetentionDays=1 \
  --tags Project=assetiq Environment=staging ManagedBy=cloudformation \
         CostCenter=engineering Team=platform
```

### 2. Backend image

The instance is Graviton (`t4g.small`), so the image **must** be `linux/arm64`. A
mismatch fails at container start with an exec format error, not at build.

```bash
SHA=$(git rev-parse --short HEAD)
REPO=369736283466.dkr.ecr.eu-central-1.amazonaws.com/assetiq-staging-ecr-backend

aws ecr get-login-password --region eu-central-1 \
  | docker login --username AWS --password-stdin "${REPO%%/*}"

docker build --platform linux/arm64 -t "$REPO:$SHA" .
docker push "$REPO:$SHA"
```

Tags are immutable in ECR: pushing over an existing tag is rejected. Use the git
SHA so a running container is always traceable to an exact commit.

### 3. Compute and CDN

```bash
aws cloudformation deploy \
  --template-file infra/aws/compute-cdn.yaml \
  --stack-name assetiq-staging-compute \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides ImageTag=$SHA SubnetId=subnet-06a1256f7cc04c2bd
```

### 4. Frontend

```bash
cd ../Enterprise-Asset-manager-Frontend
npm run build          # emits out/ (output: "export")

WEB_BUCKET=$(aws cloudformation describe-stacks --stack-name assetiq-staging-foundation \
  --query "Stacks[0].Outputs[?OutputKey=='WebBucketName'].OutputValue" --output text)
DIST=$(aws cloudformation describe-stacks --stack-name assetiq-staging-compute \
  --query "Stacks[0].Outputs[?OutputKey=='DistributionId'].OutputValue" --output text)

# Hashed assets are immutable and cached hard; HTML must revalidate or a deploy
# leaves browsers on the previous build indefinitely.
aws s3 sync out/ "s3://$WEB_BUCKET/" --delete \
  --exclude "*.html" --cache-control "public,max-age=31536000,immutable"
aws s3 sync out/ "s3://$WEB_BUCKET/" --delete \
  --exclude "*" --include "*.html" --cache-control "no-cache"

aws cloudfront create-invalidation --distribution-id "$DIST" --paths "/*"
```

## Security decisions worth knowing before you change anything

- **The instance has no SSH.** No key pair, no port 22. Shell access is
  `aws ssm start-session`, which needs no inbound rule and is logged to CloudTrail.
- **Ingress is the CloudFront prefix list, not `0.0.0.0/0`.** The origin speaks
  plain HTTP; opening it would expose the API unencrypted and let anyone bypass
  the CDN, including the edge function described next.
- **An edge function deletes `X-Client-ID` and `X-Forwarded-For` on `/api/*`.**
  This is not cosmetic. `RateLimitingInterceptor` trusts those headers from a
  trusted proxy, and the origin request policy forwards every viewer header, so
  without the function any caller could rotate `X-Client-ID` to defeat the auth
  brake — reintroducing the bypass fixed in `7da1c1c`. CloudFront also *appends*
  to a client-supplied `X-Forwarded-For` rather than replacing it, so the leftmost
  entry the interceptor reads would otherwise be attacker-controlled.
- **No secret is in user data, an image, or a parameter.** CloudFormation and RDS
  generate them; the instance fetches them at boot under its own role and writes
  `/etc/assetiq.env` with mode `600`, with shell tracing disabled around the
  fetch so values never reach the cloud-init log.
- **IMDSv2 is required**, so an SSRF in the application cannot read the instance
  role's credentials with a plain GET.

## Tearing a stack down

Two resources deliberately resist deletion, so `delete-stack` fails until each is
cleared by hand. Both are working as intended, and both will confuse you at 2am:

```bash
# RDS has DeletionProtection: true
aws rds modify-db-instance --db-instance-identifier assetiq-staging-rds-main \
  --no-deletion-protection --apply-immediately

# ECR refuses to delete a repository that still holds images, including the
# untagged attestation manifests buildx pushes alongside the tagged one.
aws ecr batch-delete-image --repository-name assetiq-staging-ecr-backend \
  --image-ids $(aws ecr list-images --repository-name assetiq-staging-ecr-backend \
                  --filter tagStatus=ANY --query 'imageIds[*]' --output json)
```

The S3 buckets and the KMS key carry `DeletionPolicy: Retain`, so they survive the
stack on purpose. A retained KMS key costs about a dollar a month; schedule its
deletion explicitly when retiring an environment for good.

## Known gaps

Honest list; none are oversights.

1. **CloudFront to the origin is HTTP.** Viewer traffic is HTTPS, but the edge-to-EC2
   hop crosses the public internet in the clear. Production needs a certificate on
   the origin or an ALB in front.
2. **Single instance, single AZ.** ShedLock now makes multiple replicas safe, but
   this deployment does not run them. An instance failure is a full outage until
   the stack redeploys.
3. **The backend does not currently start.** Hibernate validates the entities
   against the migrated schema and fails: 184 columns the entities expect are never
   created by a migration, and 83 more disagree on type. This is a pre-existing
   application defect, not a deployment one, and it blocks the API on any host. See
   `docs/SCHEMA_DRIFT.md`. The frontend is unaffected and fully served.
4. **The Paystack secret is a generated placeholder.** The application refuses to
   boot without one, so it exists to satisfy `StartupSecurityValidator`. Billing
   flows will fail until a real key is set. Replace it directly in Secrets Manager;
   do not commit it or pass it as a parameter.
5. **Email is disabled** (`APP_EMAIL_ENABLED=false`). Signup verification and
   password reset generate tokens that are never delivered. Needs SES or SMTP
   credentials.
6. **No CD.** Every step above is manual. Wiring it into GitHub Actions is Phase 1.5.
7. **The restore has not been rehearsed.** See `docs/DR.md`; the figures there are
   design intent until the log records a real restore.

## Costs

Roughly **$32/month**: EC2 `t4g.small` ~$12, RDS `db.t4g.micro` ~$13, 30 GB gp3
~$3, S3 and CloudFront a few dollars at staging traffic. The Elastic IP is free
while associated with a running instance and billed if left dangling — deleting
the compute stack releases it.
