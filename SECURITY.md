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

## Disclosure Policy

We follow **coordinated disclosure**. Once a fix is released we will publish a security advisory on GitHub. Credit will be given to reporters unless they prefer to remain anonymous.

