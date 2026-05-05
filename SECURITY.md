# Security Policy

## Supported Versions

| Version   | Supported          |
|-----------|--------------------|
| `main`    | ✅ Yes              |
| `develop` | ⚠️ Best-effort only |
| Others    | ❌ No               |

## Reporting a Vulnerability

**Please do NOT open a public GitHub issue for security vulnerabilities.**

Use GitHub's built-in private vulnerability reporting:

1. Go to the **Security** tab of this repository.
2. Click **"Report a vulnerability"**.
3. Fill in the description, steps to reproduce, and the potential impact.

We will acknowledge the report within **48 hours** and aim to deliver a patch for critical/high-severity issues within **14 days**.

## Scope

In scope:
- `constant-tracker-app` (Spring Boot WebFlux API)
- `constant-extractor-*` libraries
- `search-ui` (React frontend)

Out of scope:
- `demo-crud-server` / `demo-crud-server-v2` (example projects only)
- Local `docker-compose.yml` credentials (non-production placeholders)
- Third-party dependencies (report directly to the upstream maintainer; we will update our dependency once a fix is released)

## Auth Implementation

- **JWT access tokens** (HS256, configurable expiry, default 1 h) generated with [jjwt](https://github.com/jwtk/jjwt).
- **Refresh tokens** are opaque random UUIDs stored in PostgreSQL. A token is rotated on every `/auth/refresh` call — the previously issued token is revoked before the new one is saved.
- **Two-tier blacklist**: revoked access tokens are stored in Redis (fast path) with TTL equal to the token's remaining lifetime. Expired entries are cleaned up by `AuthCleanupScheduler` on a nightly cron schedule.
- **Passwords** are stored as BCrypt hashes (via Spring Security `PasswordEncoder`).
- **Input validation**: `LoginRequest` fields (`username`, `password`) are validated with `@NotBlank`; blank submissions are rejected with HTTP 400 before any credential check occurs.
- **`@ConditionalOnProperty`**: the entire auth subsystem (`JwtService`, `LoginController`, `AuthCleanupScheduler`) is disabled when `CONSTANTS_AUTH_ENABLED=false`.

> ⚠️ The default `CONSTANTS_AUTH_JWT_SECRET` is a dev placeholder. **You must set a strong random secret (≥ 32 chars) in production** via the `CONSTANTS_AUTH_JWT_SECRET` environment variable.

## Security Headers

The following response headers are set by `SecurityConfiguration` on every response:

| Header | Value |
|--------|-------|
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |

**HSTS is not set** — TLS is terminated by the Cloudflare tunnel upstream; the application only speaks plain HTTP within the private network. HSTS applied at the application layer would have no effect and is therefore omitted.

## CORS

CORS is configured in `WebCorsConfiguration`. Allowed origins are controlled by `CONSTANTS_CORS_ALLOWED_ORIGINS` (default: `http://localhost:5173`). Cross-origin requests with credentials are permitted; the allowed HTTP methods include `DELETE`.

## Deployment Security Notes

- The application is deployed behind a **Cloudflare Tunnel** — all public traffic is TLS-terminated by Cloudflare before reaching the cluster. Rate limiting and DDoS protection are handled at the Cloudflare layer.
- Kubernetes secrets (JWT secret, DB credentials, Redis password) are managed by Terraform and injected as `Secret` objects; they never appear in source-controlled manifests.
- The `demo-crud-server` fixtures exist purely for seeding test data and contain no real credentials or sensitive constants.

## Disclosure Policy

We follow **coordinated disclosure**. Once a fix is released we will publish a security advisory on GitHub. Credit will be given to reporters unless they prefer to remain anonymous.
