#!/usr/bin/env bash
#
# issue-offline-licence.sh — DEVELOPMENT AND VENDOR USE ONLY.
#
# Issues a signed offline licence key for a self-hosted AssetIQ installation.
# The key is an RS256 JWS carrying the organisation, plan tier, seat count and
# expiry; the backend verifies it locally against the public key baked into its
# image. There is no licence server and nothing here talks to the network.
#
#   THIS SCRIPT NEEDS THE PRIVATE KEY. Never run it on a customer's machine,
#   never put the private key in this repository, never bake it into an image,
#   and never pass it through CI as anything but a short-lived secret.
#
# ─── One-time keypair setup ──────────────────────────────────────────────────
#
#   ./scripts/licence/issue-offline-licence.sh --generate-keypair ./licence-keys
#
# That writes ./licence-keys/offline-private.pem (0600) and
# ./licence-keys/offline-public.pem. Keep the private key in a password manager
# or a KMS; it is the root of trust for every licence you will ever issue, and
# rotating it invalidates every key already in the field.
#
# Bake the PUBLIC half into the release image before building:
#
#   cp ./licence-keys/offline-public.pem \
#      src/main/resources/license/offline-public.pem
#   docker build -t assetiq-backend:<version> .
#
# ─── Issuing a key ───────────────────────────────────────────────────────────
#
#   ./scripts/licence/issue-offline-licence.sh \
#     --key       ./licence-keys/offline-private.pem \
#     --org       "Acme Bank Ltd" \
#     --plan      BUSINESS \
#     --seats     25 \
#     --expires   2027-06-30
#
# Hand the printed string to the operator; they set it as
# APP_LICENSE_OFFLINE_KEY with APP_LICENSE_OFFLINE_ENABLED=true.

set -euo pipefail

PLANS="FREEMIUM BASIC BUSINESS ENTERPRISE"

die() { echo "error: $*" >&2; exit 1; }

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

generate_keypair() {
  local dir="$1"
  mkdir -p "$dir"
  [ -e "$dir/offline-private.pem" ] && die "$dir/offline-private.pem already exists — refusing to overwrite a root of trust"

  # 4096-bit: a licence key is verified for years and signed rarely, so the
  # verification cost is irrelevant next to the longevity.
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 \
    -out "$dir/offline-private.pem" 2>/dev/null
  chmod 600 "$dir/offline-private.pem"
  openssl rsa -in "$dir/offline-private.pem" -pubout -out "$dir/offline-public.pem" 2>/dev/null
  chmod 644 "$dir/offline-public.pem"

  cat <<EOF

Keypair written.

  private  $dir/offline-private.pem   (0600 — SECRET, never commit, never ship)
  public   $dir/offline-public.pem    (safe to publish)

Next: bake the public key into the release image.

  cp $dir/offline-public.pem src/main/resources/license/offline-public.pem

EOF
}

# ── Argument parsing ─────────────────────────────────────────────────────────

PRIVATE_KEY="" ORG="" PLAN="" SEATS="" EXPIRES=""

while [ $# -gt 0 ]; do
  case "$1" in
    --generate-keypair) generate_keypair "${2:?usage: --generate-keypair <dir>}"; exit 0 ;;
    --key)     PRIVATE_KEY="${2:?}"; shift 2 ;;
    --org)     ORG="${2:?}";         shift 2 ;;
    --plan)    PLAN="${2:?}";        shift 2 ;;
    --seats)   SEATS="${2:?}";       shift 2 ;;
    --expires) EXPIRES="${2:?}";     shift 2 ;;
    -h|--help) sed -n '2,45p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[ -n "$PRIVATE_KEY" ] || die "--key is required (path to offline-private.pem)"
[ -r "$PRIVATE_KEY" ] || die "cannot read private key at $PRIVATE_KEY"
[ -n "$ORG" ]         || die "--org is required"
[ -n "$PLAN" ]        || die "--plan is required (one of: $PLANS)"
[ -n "$EXPIRES" ]     || die "--expires is required (YYYY-MM-DD)"

# shellcheck disable=SC2076
[[ " $PLANS " == *" $PLAN "* ]] || die "--plan must be one of: $PLANS"

# Expiry -> epoch seconds. BSD date (macOS) and GNU date disagree on flags.
if date -j >/dev/null 2>&1; then
  EXP_EPOCH=$(date -j -u -f "%Y-%m-%d %H:%M:%S" "$EXPIRES 23:59:59" "+%s")
else
  EXP_EPOCH=$(date -u -d "$EXPIRES 23:59:59" "+%s")
fi
NOW_EPOCH=$(date -u "+%s")
[ "$EXP_EPOCH" -gt "$NOW_EPOCH" ] || die "--expires is in the past"

# ── Build and sign the JWS ───────────────────────────────────────────────────

HEADER=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)

if [ -n "$SEATS" ]; then
  case "$SEATS" in (*[!0-9]*|'') die "--seats must be a positive integer" ;; esac
  SEATS_CLAIM=",\"seats\":$SEATS"
else
  SEATS_CLAIM=""
fi

# jq is not assumed; the organisation is the only free-text field, so escape
# the two characters that could break out of the JSON string.
ORG_ESCAPED=$(printf '%s' "$ORG" | sed 's/\\/\\\\/g; s/"/\\"/g')

PAYLOAD=$(printf '{"sub":"%s","iss":"assetiq-license","iat":%s,"exp":%s,"plan":"%s"%s}' \
  "$ORG_ESCAPED" "$NOW_EPOCH" "$EXP_EPOCH" "$PLAN" "$SEATS_CLAIM" | b64url)

SIGNING_INPUT="${HEADER}.${PAYLOAD}"
SIGNATURE=$(printf '%s' "$SIGNING_INPUT" \
  | openssl dgst -sha256 -sign "$PRIVATE_KEY" -binary \
  | b64url)

echo ""
echo "Licence key for ${ORG} — ${PLAN}${SEATS:+, ${SEATS} seats}, expires ${EXPIRES}:"
echo ""
echo "${SIGNING_INPUT}.${SIGNATURE}"
echo ""
echo "The operator sets this as APP_LICENSE_OFFLINE_KEY (with"
echo "APP_LICENSE_OFFLINE_ENABLED=true). It is not a secret in the usual sense —"
echo "it grants only this installation's own entitlement — but treat it as"
echo "customer-confidential."
echo ""
