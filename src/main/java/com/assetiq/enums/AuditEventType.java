package com.assetiq.enums;

/**
 * Categorises every audit_event row so consumers can filter structured RBAC
 * and security events without pattern-matching on the path column.
 *
 * Values are stored as VARCHAR in the database (column: event_type).
 */
public enum AuditEventType {

    // ── HTTP request events ───────────────────────────────────────────────────

    /** A regular API request (default for all HTTP calls). */
    API_REQUEST,

    // ── Authentication events ─────────────────────────────────────────────────

    /** User authenticated successfully (login, SSO callback, MFA verified). */
    AUTH_SUCCESS,

    /** Authentication attempt failed (bad credentials, expired token, MFA wrong code). */
    AUTH_FAILURE,

    // ── Authorisation events ──────────────────────────────────────────────────

    /** An authenticated user was denied access to a resource (HTTP 403). */
    PERMISSION_DENIED,

    /** A rate-limit threshold was reached for a client key. */
    RATE_LIMIT_EXCEEDED,

    // ── RBAC change events ────────────────────────────────────────────────────

    /** A new role was created. */
    ROLE_CREATED,

    /** A role's metadata (name, description) was updated. */
    ROLE_UPDATED,

    /** A role's permission set was changed (permissions added or removed). */
    ROLE_PERMISSIONS_CHANGED,

    /** A role was deleted. */
    ROLE_DELETED,

    /** A user's role assignment was changed. */
    USER_ROLE_ASSIGNED,

    /** A user account was created. */
    USER_CREATED,

    /** A user account was updated. */
    USER_UPDATED,

    /** A user account was deleted or deactivated. */
    USER_DELETED,
}
