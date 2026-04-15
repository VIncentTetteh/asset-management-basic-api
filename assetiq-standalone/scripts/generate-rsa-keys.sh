#!/usr/bin/env bash
# generate-rsa-keys.sh
#
# Generates the RSA-2048 key pair used by the AssetIQ License Server to sign
# and verify license JWTs.  Run this ONCE before the first `docker compose up`.
#
# Output:
#   keys/private.pem  — kept on the license server only (never share)
#   keys/public.pem   — copy into the backend app bundle at:
#                       Enterprise-Asset-Manager/src/main/resources/license/public.pem
#
# Requires: openssl (pre-installed on macOS and most Linux distros)

set -euo pipefail

KEYS_DIR="$(cd "$(dirname "$0")/.." && pwd)/keys"
mkdir -p "$KEYS_DIR"

echo "→ Generating RSA-2048 private key…"
openssl genrsa -out "$KEYS_DIR/private.pem" 2048

echo "→ Deriving public key…"
openssl rsa -in "$KEYS_DIR/private.pem" -pubout -out "$KEYS_DIR/public.pem"

chmod 600 "$KEYS_DIR/private.pem"
chmod 644 "$KEYS_DIR/public.pem"

echo ""
echo "✅  Keys written to $KEYS_DIR"
echo ""
echo "IMPORTANT — next step:"
echo "  Copy keys/public.pem into your backend bundle so standalone instances"
echo "  can verify license signatures without calling the license server:"
echo ""
echo "  cp $KEYS_DIR/public.pem \\"
echo "     Enterprise-Asset-Manager/src/main/resources/license/public.pem"
echo ""
echo "  Never commit keys/private.pem to version control."
