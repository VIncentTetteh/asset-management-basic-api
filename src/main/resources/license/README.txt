Place public.pem here before building the Docker image.

Generate with:
  cd assetiq-standalone
  ./scripts/generate-rsa-keys.sh
  cp keys/public.pem ../Enterprise-Asset-Manager/src/main/resources/license/public.pem

This RSA-2048 public key is bundled into the standalone backend so it can
verify license JWTs locally without reaching the license server on every request.
DO NOT commit your private key here. Only public.pem belongs in source control.
