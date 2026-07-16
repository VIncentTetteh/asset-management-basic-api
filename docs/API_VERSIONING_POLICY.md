# AssetIQ API Versioning Policy

## Overview

AssetIQ uses **URL path versioning** for its REST API. All endpoints are prefixed with `/api/v{N}/` where `N` is a monotonically increasing integer (e.g., `/api/v1/assets`).

## Current Version

**v1** — Stable. All production clients must target v1 endpoints.

## Versioning Rules

### When a new version is required

A new API version is **mandatory** when a change is **breaking** for existing consumers. Breaking changes include:

- Removing a field from a response body
- Renaming a field in a request or response body
- Changing a field's type (e.g., `String` → `Integer`)
- Removing or renaming an endpoint
- Changing a required request parameter format
- Altering authentication or authorization semantics (e.g., changing which roles can call an endpoint)

### When a new version is NOT required

Additive, non-breaking changes do **not** require a new version:

- Adding a new optional field to a response
- Adding a new optional query parameter
- Adding a new endpoint
- Bug fixes that do not change the public contract
- Performance improvements or internal refactors

## Deprecation Process

1. **Announce** — Deprecated endpoints are flagged with a `Deprecation: true` response header and a note in the changelog.
2. **Sunset period** — A minimum of **6 months** between the deprecation announcement and endpoint removal.
3. **Sunset header** — Deprecated endpoints return a `Sunset: <RFC 1123 date>` header (per RFC 8594) indicating the planned removal date.
4. **Removal** — Only after the sunset date passes, with at least one release note warning ahead of time.

## Compatibility Guarantee

| Change type | Backward compatible? | New version needed? |
|-------------|----------------------|---------------------|
| Add optional response field | Yes | No |
| Add optional request param | Yes | No |
| Add new endpoint | Yes | No |
| Remove/rename field | **No** | **Yes** |
| Change field type | **No** | **Yes** |
| Remove endpoint | **No** | **Yes** |

## Implementation Notes

- Version routing is handled by Spring MVC's `@RequestMapping("/api/v1/...")` on each controller class.
- Controllers live in `com.assetiq.controllers.v1` (future versions in `v2`, `v3`, etc.).
- A future `ApiVersionFilter` will add `API-Version: 1` to all responses automatically.
- OpenAPI/Swagger docs (`/v3/api-docs`, `/swagger-ui.html`) should be kept in sync per version.

## Adding v2: Step-by-Step

1. Create new controller(s) in `com.assetiq.controllers.v2` with `@RequestMapping("/api/v2/...")`.
2. Add `Deprecation: true` and `Sunset: <date>` response headers to the affected v1 controller methods.
3. Update the OpenAPI `@Tag` annotations to document both versions.
4. Add a changelog entry describing what changed and why.
5. After the sunset date, remove the v1 endpoint in a dedicated PR tagged with the major release.

## References

- [RFC 8594 — The Sunset HTTP Header Field](https://www.rfc-editor.org/rfc/rfc8594)
- [RFC 8288 — Web Linking (Deprecation link relation)](https://www.rfc-editor.org/rfc/rfc8288)
- [Semantic Versioning 2.0](https://semver.org/)
