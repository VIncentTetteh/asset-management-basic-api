# Regulated General-Availability Release Gate

AssetIQ must not be represented or tagged as generally available for a bank,
telco, fintech, or other regulated enterprise until this gate passes.

The first supported commercial scope is the API and web application. Mobile and
desktop builds remain preview clients until their separate device, distribution,
offline-data, and security assessments are approved.

## How the gate works

1. Evidence owners update `regulated-ga-evidence.json` for a specific immutable
   release candidate.
2. Every required attestation must be `approved`, identify an accountable human
   approver and approval date, reference the controlled evidence location, and
   include the evidence file's SHA-256 digest.
3. Expiring evidence must remain valid on the day of release.
4. Run:

   ```bash
   python3 scripts/verify_regulated_ga_evidence.py \
     --manifest docs/commercial-readiness/regulated-ga-evidence.json \
     --release-candidate regulated-ga-YYYY.MM.DD.N
   ```

5. The `regulated-ga-gate` GitHub workflow runs the same validation for manual
   approvals and every `regulated-ga-*` tag.

The manifest records attestations, not confidential reports. Evidence should be
stored in the approved document/control system with immutable retention and
access logs. A URI and digest do not replace review; they bind the approval to
the reviewed artifact.

## Required independent and operational evidence

- Counsel-approved terms, privacy notice, DPA, cookie position, subprocessors,
  retention schedule, and versioned customer acceptance process.
- Operational support desk, severity matrix, on-call/escalation coverage,
  customer communications, status mechanism, and contracted SLA wording.
- Independent web/API/cloud penetration test with critical/high findings closed
  and an assessor-issued retest result.
- Threat model and security architecture review covering tenant isolation,
  identity, secrets, uploads, audit, integrations, and privileged operations.
- Timed backup restore and failure exercise with measured RPO/RTO.
- Production-like load/soak/capacity evidence and at least 30 days of agreed SLO
  observations for the release environment.
- WCAG 2.2 AA manual accessibility assessment and remediation evidence in
  addition to automated axe checks.
- Qualified legal/compliance approval of any jurisdiction or regulator mapping,
  including the current Bank of Ghana directive where applicable.
- Commercial quote/order/PO/invoice/tax/payment/refund/reconciliation and
  renewal/cancellation operating procedures tested by Finance.
- Privacy/data-flow review, breach/DSAR/retention exercises, and security pack.
- Controlled pilot acceptance, incident record, reconciliation result, and
  accountable executive launch approval.

## Prohibited shortcuts

- Do not mark an item approved because a design document exists.
- Do not use an engineer as the legal, regulatory, independent-assurance, or
  executive approver.
- Do not point multiple controls at an empty placeholder.
- Do not alter or reuse an evidence digest after the underlying report changes.
- Do not publish uptime, response-time, certification, compliance, or support
  claims that exceed the approved evidence and customer agreement.

