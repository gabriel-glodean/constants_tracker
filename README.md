# Constant Tracker

> **Static analysis for the constants your code review misses.**

Most tooling focuses on the structure of your Java code — class hierarchies, call graphs, dependency cycles. Constant Tracker focuses on the *values*: every hardcoded string and number baked into your bytecode and config files, tracked across versions, searchable by meaning.

Upload a `.class` file, a JAR, or a YAML/`.properties` file. Constant Tracker walks the bytecode instruction-by-instruction — tracking values through local variables, the operand stack, and control flow branches — and surfaces every constant along with a two-layer classification:

- **Structural type** — *how* the constant is used at the JVM level: passed as a method parameter, stored in a field, used as an arithmetic operand, embedded in a string concatenation, or bound in an annotation.
- **Semantic type** — *what* the constant represents: SQL fragment, URL, file path, log message, error message, API endpoint, regex pattern, MIME type, date format, and more. Each classification carries a confidence score.

The result is a searchable, versioned index of every meaningful value scattered across your codebase — with diffs between releases and, on the roadmap, detection of the same constant appearing silently in multiple independent projects.

This extends to `CONSTANT_Dynamic` (condy) — the modern JVM mechanism used by Kotlin, Groovy, and Java's own string concatenation and pattern matching to produce constants lazily at link time via bootstrap methods. Condy entries are completely invisible to method-body analysis; they never appear as standalone `ldc` instructions. Most bytecode tools ignore them entirely. Constant Tracker is building explicit support to resolve and track them — and to detect when two independently deployed components share the same bootstrap method recipe, a coupling deeper and harder to spot than any value-level match.

---

![Search](./docs/search.jpg)

---

## What it does

| Feature | Description |
|---------|-------------|
| 🔬 **Bytecode analysis** | Walks JVM bytecode using a worklist/dataflow algorithm — not regex. Handles branches, loops, exception handlers, annotations, and `static final` fields. Uses Java 25's Class-File API. |
| 📦 **Fat JAR support** | Recursively extracts nested JARs (`BOOT-INF/lib/`, `WEB-INF/lib/`, shaded root). Each nested JAR is indexed separately with content-hash deduplication — re-uploading an unchanged library is a no-op. |
| ⚙️ **Config file extraction** | Extracts constants from `.yml`, `.yaml`, and `.properties` files — including config files embedded inside JARs — through the same pipeline as bytecode. |
| 🏷️ **Two-layer classification** | Every constant gets a structural type (JVM usage) and a semantic type (meaning), each with a confidence score. Built-in classifiers: SQL, URL, File Path, Log Message, Error Message, Annotation Value. Extensible via SPI. |
| 🔍 **Search** | Full-text keyword search across all indexed constants, filterable by semantic type. |
| 🔀 **Version diff** | Compare two finalized versions of a project and see exactly which constants were added, removed, or changed — per class. |
| 🗂️ **Version manager** | Create, finalize, sync removals, and delete project versions via UI or API. |

---

## Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3 / WebFlux, Java 25 |
| Bytecode parsing | Java 25 Class-File API (`java.lang.classfile`) |
| Search | Solr 10 |
| Database | PostgreSQL 17 (R2DBC + Flyway) |
| Cache | Redis 7 |
| UI | React 19 + TypeScript, Vite, Tailwind CSS, Nginx |

---

## Quick Start (local)

**Prerequisites:** Docker, Docker Compose, JDK 25, Node 22

```bash
git clone https://github.com/gabrielglodean/constant-tracker.git
cd constant-tracker
cp .env.example .env          # fill in credentials
docker compose --profile=seed up -d
```

| Service | URL |
|---------|-----|
| UI | http://localhost:5173 |
| API | http://localhost:8080 |
| Solr admin | http://localhost:8983/solr/#/ |

The `seed` profile uploads two versions of a bundled demo CRUD server so you can explore all features immediately:

- Search `SELECT` or `http://` to see extracted and classified constants
- Diff `demo-crud-server` version `1` → `2` to see what changed between releases

### Compose profiles

| Profile | Command | Effect |
|---------|---------|--------|
| *(none)* | `docker compose up -d` | Start all services |
| `seed` | `docker compose --profile=seed up -d` | Start + upload demo data |
| `clear` | `docker compose --profile=clear up clear` | Wipe Postgres, Solr, Redis |

**Re-seed from scratch:**
```bash
docker compose --profile=clear up clear
docker compose down            # tears down the stale network
docker compose --profile=seed up -d
```

### Troubleshooting: Solr permission error on first start

```bash
sudo rm -rf ./.solr_data
sudo mkdir -p ./.solr_data
sudo chown -R 8983:8983 ./.solr_data
docker compose up -d solr
```

---

## API

All endpoints are documented at **http://localhost:8080/swagger-ui.html**.

When auth is enabled, obtain a token first, then pass it as a Bearer header:

```bash
# Sign in
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"secret"}' | jq -r .accessToken)

# Upload a .class file
curl -X POST "http://localhost:8080/class?project=myproject" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @MyClass.class

# Upload a JAR (streamed — full JAR never loaded into heap)
curl -X POST "http://localhost:8080/jar?project=myproject&jarName=myapp.jar" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @myapp.jar
```

---

## Authentication

Auth is **enabled by default** using short-lived JWT access tokens (1 h) and long-lived refresh tokens (7 days), stored in PostgreSQL with a Redis-backed blacklist. Set `CONSTANTS_AUTH_ENABLED=false` to run without auth.

**Token lifecycle:**
- `POST /auth/login` → `{ accessToken, refreshToken }`
- `POST /auth/refresh` → new `{ accessToken, refreshToken }` *(refresh token rotates on every call — always store the new one)*
- `POST /auth/logout` → blacklists the access token and revokes the refresh token

| Env var | Default | Purpose |
|---------|---------|---------|
| `CONSTANTS_AUTH_ENABLED` | `true` | Set `false` to disable auth entirely |
| `CONSTANTS_AUTH_JWT_SECRET` | *(dev placeholder)* | **Change in production** — min 32 chars |
| `CONSTANTS_AUTH_JWT_EXPIRATION_MS` | `3600000` | Access token lifetime (ms) |
| `CONSTANTS_AUTH_REFRESH_TOKEN_TTL_MS` | `604800000` | Refresh token lifetime (ms) |
| `CONSTANTS_AUTH_CLEANUP_BLACKLIST_CRON` | `0 0 2 * * *` | Cron for purging expired blacklist rows |
| `CONSTANTS_AUTH_CLEANUP_REFRESH_TOKENS_CRON` | `0 15 2 * * *` | Cron for purging expired refresh tokens |

---

## Production (k3s / Kubernetes)

Deployment is managed via Terraform + k3s. Requires a server with k3s installed and a Cloudflare Tunnel pointed at `http://localhost:80`.

```bash
cp terraform.tfvars.example terraform.tfvars   # fill in server IP, SSH key, credentials
terraform init
terraform apply
```

Terraform will:
1. Install k3s if not present
2. Copy k8s manifests and Solr config to the server
3. Apply all manifests in order: namespace → ConfigMaps → Redis → Postgres → Solr → app → UI → ingress
4. Create secrets
5. Seed demo data if JAR hashes have changed

Re-deploy after code or manifest changes: `terraform apply` — triggers are based on JAR and k8s manifest hashes.

**Manual seed/clear on the server:**
```bash
kubectl delete job seed-job -n constant-tracker --ignore-not-found=true
kubectl apply -f /opt/constant-tracker/k8s/jobs.yml --selector=app=seed-job
kubectl wait --for=condition=complete job/seed-job -n constant-tracker --timeout=300s
```

---

## Build & Test

```bash
# Backend
./gradlew test                                       # all tests
./gradlew testReport                                 # HTML report → build/reports/allTests/
./gradlew :constant-extractor-bytecode:check         # tests + JaCoCo ≥ 85%
./gradlew :constant-extractor-config-file:check      # tests + JaCoCo ≥ 85%
./gradlew :constant-tracker-app:check                # tests + JaCoCo ≥ 85%
./gradlew :constant-tracker-app:heavyTest            # 16 GB heap; full-runtime analysis
./gradlew spotlessApply                              # auto-format

# UI
cd search-ui && npm test -- --watchAll=false         # 212 Jest tests
```

---

## Modules

| Module | Description |
|--------|-------------|
| `constant-extractor-api` | Shared model + SPI (`ModelExtractor`, `ConstantUsageInterpreter`). No framework dependencies. |
| `constant-extractor-bytecode` | JVM bytecode analysis: worklist/dataflow engine, CFG builder, per-opcode instruction handlers, 6 built-in semantic classifiers. |
| `constant-extractor-config-file` | YAML + `.properties` extraction (same SPI, plugged in alongside bytecode). |
| `constant-tracker-app` | Spring Boot / WebFlux service: REST API, Solr indexing, PostgreSQL persistence, Redis cache, JWT auth. |
| `search-ui` | React UI: search, upload, class lookup, version diff, version manager. |
| `demo-crud-server` (v1/v2) | Demo fixtures for seeding and showing diffs between releases. |

---

## Roadmap highlights

- **JAR extraction status** — `POST /jar` returns a job ID; `GET /jar/status/{id}` for polling; UI progress badge.
- **Cross-project coupling detection** — `GET /search/shared?value=...` finds the same constant across independently indexed projects. Constants shared across components and used in conditional logic are flagged as high-risk couplings.
- **`CONSTANT_Dynamic` (condy) support** — the most underexplored area of JVM constant analysis. Condy entries are baked into the `BootstrapMethods` attribute and never surface as `ldc` instructions — every existing method-body walker misses them completely. Constant Tracker is adding two layers of condy coverage:
  - *Static resolution* — well-known bootstrap methods (`ConstantBootstraps.getStaticFinal`, Kotlin reflection, Groovy metaclass) are resolved to their concrete values and fed into the same classification pipeline as ordinary constants.
  - *BSM-level coupling detection* — when two independently deployed components reference the same `DynamicConstantDesc` bootstrap (same owner, name, and static arguments), that is a call-site-recipe coupling: both components depend on the same code to produce a value at link time. This is a harder and more invisible form of hidden dependency than value equality — and Constant Tracker will be the tool that surfaces it.
- **Pagination** — search results beyond the current 100-row cap.
- **Solr → Postgres-native search** — `pg_trgm` + `pgvector` replacement is planned once all query patterns are known (the store layer is already behind an SPI).

---

## Screenshots

| | |
|--|--|
| ![Upload](./docs/upload.jpg) | ![Search](./docs/search.jpg) |
| ![Class lookup](./docs/lookup.jpg) | ![Diff](./docs/diff.jpg) |
| ![Diff details](./docs/details.jpg) | ![Version manager](./docs/versions.jpg) |

---

## License

MIT © Gabriel Glodean
