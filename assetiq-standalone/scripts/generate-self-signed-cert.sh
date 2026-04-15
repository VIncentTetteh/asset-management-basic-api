#!/usr/bin/env bash
# generate-self-signed-cert.sh
#
# Creates a self-signed TLS certificate for local / intranet use.
# For production, replace with a certificate from Let's Encrypt or your CA.
#
# Output:
#   nginx/certs/server.crt
#   nginx/certs/server.key

set -euo pipefail

CERTS_DIR="$(cd "$(dirname "$0")/../nginx/certs" && pwd)"
mkdir -p "$CERTS_DIR"

DOMAIN="${1:-localhost}"

echo "→ Generating self-signed certificate for: $DOMAIN"

openssl req -x509 \
  -newkey rsa:4096 \
  -keyout "$CERTS_DIR/server.key" \
  -out    "$CERTS_DIR/server.crt" \
  -days   825 \
  -nodes  \
  -subj   "/C=US/ST=State/L=City/O=AssetIQ/CN=${DOMAIN}" \
  -addext "subjectAltName=DNS:${DOMAIN},DNS:www.${DOMAIN},IP:127.0.0.1"

chmod 600 "$CERTS_DIR/server.key"
chmod 644 "$CERTS_DIR/server.crt"

echo "✅  Certificate written to $CERTS_DIR"
echo "    server.crt  (certificate)"
echo "    server.key  (private key)"
echo ""
echo "Browsers will show a security warning for self-signed certificates."
echo "To trust it on macOS: open $CERTS_DIR/server.crt and add to Keychain."
