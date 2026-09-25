#!/usr/bin/env python3
"""Fail closed unless all regulated-GA attestations are complete and current."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

SHA256 = re.compile(r"^[0-9a-f]{64}$")
REQUIRED_FIELDS = ("evidenceUri", "sha256", "approvedBy", "approvedAt")


def parse_instant(value: str, field: str, item_id: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{item_id}: {field} must be an ISO-8601 timestamp") from exc
    if parsed.tzinfo is None:
        raise ValueError(f"{item_id}: {field} must include a timezone")
    return parsed.astimezone(timezone.utc)


def validate(manifest: dict, release_candidate: str) -> list[str]:
    errors: list[str] = []
    if manifest.get("schemaVersion") != 1:
        errors.append("schemaVersion must be 1")
    if manifest.get("releaseCandidate") != release_candidate:
        errors.append("manifest releaseCandidate must exactly match the requested release")
    if manifest.get("supportedClients") != ["api", "web"]:
        errors.append("regulated GA currently supports exactly the api and web clients")

    attestations = manifest.get("attestations")
    if not isinstance(attestations, list) or not attestations:
        return errors + ["attestations must be a non-empty array"]

    now = datetime.now(timezone.utc)
    seen: set[str] = set()
    for item in attestations:
        item_id = item.get("id", "<missing-id>") if isinstance(item, dict) else "<invalid-item>"
        if not isinstance(item, dict):
            errors.append(f"{item_id}: attestation must be an object")
            continue
        if item_id in seen:
            errors.append(f"{item_id}: duplicate attestation id")
        seen.add(item_id)
        if item.get("status") != "approved":
            errors.append(f"{item_id}: status is not approved")
        for field in REQUIRED_FIELDS:
            value = item.get(field)
            if not isinstance(value, str) or not value.strip():
                errors.append(f"{item_id}: {field} is required")
        digest = item.get("sha256")
        if isinstance(digest, str) and not SHA256.fullmatch(digest):
            errors.append(f"{item_id}: sha256 must be 64 lowercase hexadecimal characters")
        if isinstance(item.get("approvedAt"), str) and item.get("approvedAt"):
            try:
                approved_at = parse_instant(item["approvedAt"], "approvedAt", item_id)
                if approved_at > now:
                    errors.append(f"{item_id}: approvedAt cannot be in the future")
            except ValueError as exc:
                errors.append(str(exc))
        expires_at = item.get("expiresAt")
        if expires_at is not None:
            if not isinstance(expires_at, str) or not expires_at:
                errors.append(f"{item_id}: expiresAt must be null or an ISO-8601 timestamp")
            else:
                try:
                    if parse_instant(expires_at, "expiresAt", item_id) <= now:
                        errors.append(f"{item_id}: evidence has expired")
                except ValueError as exc:
                    errors.append(str(exc))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--release-candidate", required=True)
    args = parser.parse_args()

    if not re.fullmatch(r"regulated-ga-\d{4}\.\d{2}\.\d{2}\.\d+", args.release_candidate):
        print("release candidate must match regulated-ga-YYYY.MM.DD.N", file=sys.stderr)
        return 2
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"cannot read evidence manifest: {exc}", file=sys.stderr)
        return 2

    errors = validate(manifest, args.release_candidate)
    if errors:
        print("REGULATED GA BLOCKED", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(f"REGULATED GA EVIDENCE PASSED: {args.release_candidate}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

