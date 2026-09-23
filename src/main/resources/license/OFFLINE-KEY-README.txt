Offline licence public key — where it goes and why it is not in git
===================================================================

The self-hosted SKU (APP_LICENSE_OFFLINE_ENABLED=true) verifies its licence key
against an RSA public key baked into the image at:

    src/main/resources/license/offline-public.pem

That file is deliberately ABSENT from this repository. It is the public half of
a keypair whose private half is the root of trust for every licence the vendor
will ever issue, and committing a public key here without a managed home for the
private key would create a trust anchor nobody owns. The release build copies it
in; see docs/self-hosting.md and scripts/licence/issue-offline-licence.sh.

Generate the keypair once:

    ./scripts/licence/issue-offline-licence.sh --generate-keypair ./licence-keys

Bake the public half in before building the release image:

    cp ./licence-keys/offline-public.pem src/main/resources/license/offline-public.pem
    docker build -t assetiq-backend:<version> .

Never commit offline-private.pem. .gitignore refuses both *.pem in this
directory and the licence-keys/ output directory, but the real safeguard is that
the private key belongs in a password manager or KMS, not on a build host.

If no public key is baked in, the application still starts. It logs that it has
no trust anchor and resolves every installation to the free tier. A licence
problem is never an outage.

---

The separate file public.pem in this directory belongs to the OLDER, online
licence path (APP_MODE=standalone, com.assetiq.license.LicenseService), which
calls a vendor licence server every 24 hours. The two are independent. New
self-hosted deployments should use the offline mode described above; see
docs/self-hosting.md for why.
