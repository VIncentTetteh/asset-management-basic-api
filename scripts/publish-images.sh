#!/usr/bin/env bash
#
# publish-images.sh — build the AssetIQ backend and web images for
# linux/amd64 + linux/arm64 and push them to ECR Public.
#
# Usually invoked through the Makefile:
#
#   make images-build                       # build only, load nothing, push nothing
#   make images-push ECR_ALIAS=yourAlias    # build + push
#
# ─── One-time ECR Public setup (the operator does this once) ─────────────────
#
# ECR Public lives in us-east-1 only, regardless of where you run anything.
#
#   1. Claim a registry alias (once per AWS account):
#        https://console.aws.amazon.com/ecr/create-public-registry
#      The alias is the "assetiq" in public.ecr.aws/assetiq. Note it down.
#
#   2. Create one repository per image:
#        aws ecr-public create-repository --region us-east-1 \
#          --repository-name assetiq-backend
#        aws ecr-public create-repository --region us-east-1 \
#          --repository-name assetiq-web
#
#   3. Authenticate Docker (the token lasts 12 hours):
#        aws ecr-public get-login-password --region us-east-1 \
#          | docker login --username AWS --password-stdin public.ecr.aws
#
#   4. Confirm the pusher's IAM principal has:
#        ecr-public:GetAuthorizationToken, sts:GetServiceBearerToken,
#        ecr-public:BatchCheckLayerAvailability, ecr-public:InitiateLayerUpload,
#        ecr-public:UploadLayerPart, ecr-public:CompleteLayerUpload,
#        ecr-public:PutImage
#      scoped to the two repository ARNs, not "*".
#
#   5. Make sure buildx has a container driver (the default "docker" driver
#      cannot build multi-arch):
#        docker buildx create --name assetiq --driver docker-container --use
#        docker buildx inspect --bootstrap
#
# NOTE: An ECR Public repository is world-readable by definition. Push only
# images you are content for anyone to pull. The images carry no secrets: every
# credential is supplied at runtime through the environment.

set -euo pipefail

BACKEND_REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_REPO_DIR="${ASSETIQ_WEB_REPO:-${BACKEND_REPO_DIR}/../Enterprise-Asset-manager-Frontend}"

REGISTRY="${ASSETIQ_REGISTRY:-}"
ECR_ALIAS="${ECR_ALIAS:-}"
VERSION="${ASSETIQ_VERSION:-}"
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"
PUSH=0

die()  { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --push)      PUSH=1;            shift ;;
    --alias)     ECR_ALIAS="${2:?}"; shift 2 ;;
    --version)   VERSION="${2:?}";   shift 2 ;;
    --registry)  REGISTRY="${2:?}";  shift 2 ;;
    --platforms) PLATFORMS="${2:?}"; shift 2 ;;
    -h|--help)   sed -n '2,45p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

# ── Version: derived from git, never "latest" ────────────────────────────────
#
# `git describe` gives v1.4.2 on a tagged commit and v1.4.2-7-gabc1234 seven
# commits later, so a tag always names one exact tree. Falling back to the short
# sha keeps untagged branches publishable. A dirty tree is refused: an image
# tagged with a commit it does not actually contain is unreproducible, and the
# only time that matters is the time you need to roll back.
if [ -z "$VERSION" ]; then
  command -v git >/dev/null || die "git is required to derive a version, or pass --version"
  git -C "$BACKEND_REPO_DIR" rev-parse --git-dir >/dev/null 2>&1 \
    || die "not a git repository and no --version given"

  if [ -n "$(git -C "$BACKEND_REPO_DIR" status --porcelain)" ]; then
    die "working tree is dirty. Commit or stash before publishing, or pass an
       explicit --version. An image tagged with a commit it does not contain is
       exactly the thing you cannot debug during a rollback."
  fi
  VERSION="$(git -C "$BACKEND_REPO_DIR" describe --tags --always --dirty 2>/dev/null)"
  VERSION="${VERSION#v}"
fi
[ -n "$VERSION" ]         || die "could not determine a version"
if [ "$VERSION" = "latest" ]; then die "'latest' is not a version"; fi
case "$VERSION" in (*dirty*) die "refusing to publish a -dirty version: $VERSION" ;; esac

# ── Registry ─────────────────────────────────────────────────────────────────
if [ -z "$REGISTRY" ]; then
  [ -n "$ECR_ALIAS" ] || die "pass --alias <your-ecr-public-alias> (or --registry)"
  REGISTRY="public.ecr.aws/${ECR_ALIAS}"
fi

VCS_REF="$(git -C "$BACKEND_REPO_DIR" rev-parse --short HEAD 2>/dev/null || echo unknown)"
BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

[ -d "$WEB_REPO_DIR" ] || die "web repo not found at $WEB_REPO_DIR (set ASSETIQ_WEB_REPO)"

info "version    ${VERSION}"
info "registry   ${REGISTRY}"
info "platforms  ${PLATFORMS}"
info "push       $([ "$PUSH" -eq 1 ] && echo yes || echo 'no (build only)')"
echo ""

docker buildx version >/dev/null 2>&1 || die "docker buildx is required"

# A multi-platform build cannot be loaded into the local docker image store, so
# a no-push build is validated rather than exported. That still compiles and
# runs every stage, which is what a CI pre-merge check needs.
if [ "$PUSH" -eq 1 ]; then
  OUTPUT_ARGS=(--push)
else
  OUTPUT_ARGS=(--output "type=cacheonly")
  info "No --push: building for all platforms and discarding the result."
  info "Multi-arch images cannot be loaded locally; use 'docker build' for that."
  echo ""
fi

build() {
  local name="$1" context="$2"; shift 2
  info "building ${REGISTRY}/${name}:${VERSION}"
  docker buildx build \
    --platform "$PLATFORMS" \
    --tag "${REGISTRY}/${name}:${VERSION}" \
    --build-arg "VERSION=${VERSION}" \
    --build-arg "VCS_REF=${VCS_REF}" \
    --build-arg "BUILD_DATE=${BUILD_DATE}" \
    --provenance=true \
    --sbom=true \
    "$@" \
    "${OUTPUT_ARGS[@]}" \
    "$context"
}

build assetiq-backend "$BACKEND_REPO_DIR"

build assetiq-web "$WEB_REPO_DIR" \
  --build-arg NEXT_PUBLIC_APP_MODE=standalone \
  --build-arg NEXT_PUBLIC_API_URL=/api/v1

echo ""
if [ "$PUSH" -eq 1 ]; then
  info "Pushed:"
  echo "    ${REGISTRY}/assetiq-backend:${VERSION}"
  echo "    ${REGISTRY}/assetiq-web:${VERSION}"
  echo ""
  echo "  Operators pin this in their .env:"
  echo "    ASSETIQ_REGISTRY=${REGISTRY}"
  echo "    ASSETIQ_VERSION=${VERSION}"
else
  info "Build succeeded for ${PLATFORMS}. Nothing was pushed."
fi
echo ""

# Deliberately no 'latest' tag is ever pushed. A self-hosted operator who
# tracks a moving tag gets an unattended schema migration on their next
# container restart, at a time nobody chose.
