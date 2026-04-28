# Constant Tracker

Ever needed to audit every hardcoded SQL string, URL, or file path across a large multi-module Java project? Or see exactly which constants changed between two releases? Constant Tracker does that — upload a JAR, search every indexed constant by keyword or semantic type, and diff two versions to see what changed.

A Spring Boot (WebFlux) service that indexes **Java bytecode constants** and **configuration file constants** for fast search and analysis using Solr.

**Why it matters:** This project focuses on **bytecode analysis correctness** with advanced JVM class file parsing, constant pool resolution, and indexing. It also supports config file analysis (YAML, properties) for comprehensive constant auditing across your codebase.

---

## 🚀 Quick Start

**Prerequisites:** Docker, Docker Compose, and a few minutes for the first build (Gradle + npm).

**Step 1:** Clone and configure

```bash
git clone https://github.com/gabrielglodean/constant-tracker.git
cd constant-tracker
cp .env.example .env  # if not already present
```

**Step 2:** Start services and seed demo data

```bash
docker compose --profile=seed up -d
```

**Step 3:** Open the UI and explore

Navigate to [http://localhost:5173](http://localhost:5173). The `seed` profile automatically uploads two versions of `demo-crud-server` so you have instant data to explore.

**Try it out:**
- Upload a JAR and search for `SELECT` or `http://` to see indexed constants with semantic classifications
- Open the **Diff** tab and compare `demo-crud-server` from version `1` to `2` to see exactly what changed

![Upload a JAR file](./docs/upload.jpg)
![Search constants by keyword](./docs/search.jpg)
![Inspect constant details](./docs/lookup.jpg)
![Diff two project versions](./docs/diff.jpg)
![Diff details](./docs/details.jpg)
![Version management](./docs/versions.jpg)


---

## 🏗️ Architecture

This is a **multi-module Gradle project** designed with clear separation of concerns:

### Core Modules (5)

| Module | Purpose | Key Features |
|--------|---------|--------------|
| **`constant-extractor-api`** | Shared model & SPI | Model records, `ConstantUsageInterpreter` strategy interface, `ModelExtractor` interface, zero framework dependencies |
| **`constant-extractor-bytecode`** | Bytecode analysis engine | JVM ClassFile parser (Java 25 format), constant pool extraction, 6 semantic classifiers, >90% test coverage |
| **`constant-extractor-config-file`** | Config file analysis | YAML and Java properties extraction, reuses shared model |
| **`constant-tracker-app`** | Spring Boot web service | Reactive REST API, Redis caching, Solr indexing, Postgres persistence, version lifecycle management |
| **`search-ui`** | Web-based UI | React 19 + TypeScript, fuzzy search, class lookup, file upload, version diff viewer |

### Demo/Test Modules (2)

| Module | Purpose |
|--------|---------|
| **`demo-crud-server`** (v1) | Test fixture with hardcoded constants (SQL, URLs, file paths) |
| **`demo-crud-server-v2`** (v2) | Refactored version with `AppConfig` class + `app.properties`, enables diff demos |

### Data Flow

**Class/JAR/Config Upload & Indexing:**
```
File Upload (.class/.jar/.yml/.properties)
        ↓
WebFlux Controller (constant-tracker-app)
        ↓
Analysis Engine (bytecode / config-file extractors)
        ↓
Redis Cache + Solr Index + Postgres DB
```

**Class & Project Query:**
```
Query Request
        ↓
WebFlux Controller
        ↓
Redis Cache or Postgres DB
```

**Fuzzy Search:**
```
Search Query
        ↓
WebFlux Controller
        ↓
Solr Index (with Redis caching)
```

### Technology Stack

**Backend Stack:**
- **Spring Boot 3 / WebFlux** — Reactive REST interface
- **Solr 10** — Full-text search and constant indexing
- **Postgres 17** — Relational storage (R2DBC + Flyway migrations)
- **Redis 7** — Caching and versioning
- **Java 25** — Latest JVM features including ClassFile API

**Frontend Stack (search-ui):**
- **React 19 + TypeScript** — UI framework
- **Vite 8** — Build tool and dev server
- **Tailwind CSS v4** — Utility-first styling
- **Lucide React** — Icon library
- **ESLint** — Code linting
- **Nginx** — Production static file serving
- **Docker** — Containerization

**Analysis Libraries:**
- **JVM ClassFile API** — Java 25 bytecode parsing
- **SnakeYAML** — YAML configuration parsing
- **Guava** — Utility functions

---

## 🖥️ User Interface

Web-based UI for searching, browsing, and managing indexed constants.

**Location:** `search-ui/` directory (served as a static site via Nginx)

**Features:**
- Fuzzy search for constants across projects
- **Version diff viewer** — compare two versions of a project and see added/removed/changed constants per class
- Class constant lookup by project/class/version
- Multi-format file upload (`.class`, `.jar`, `.yml`/`.yaml`, `.properties`)
- Version management (view, finalize, sync removals, delete units)

**Access:**
- UI: [http://localhost:5173](http://localhost:5173)
- API: [http://localhost:8080](http://localhost:8080)
- API Docs: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## 🗄️ Data Stores

The application uses three main data stores for indexing and persistence:

**Solr 10** — Full-text search and constant indexing
- Default URL: [http://localhost:8983/solr/](http://localhost:8983/solr/)
- Collection name: `Constants`
- Schema: `constant-tracker-app/solr/managed-schema.xml` (auto-mounted by Docker Compose)

**Postgres 17** — Relational storage and metadata
- Default URL: `jdbc:postgresql://localhost:5432/constant_tracker`
- ⚠️ Configure database credentials via environment variables before first run

**Redis 7** — Caching and atomic versioning
- Default URL: `localhost:6379`

All services start automatically with Docker Compose. The Solr schema is auto-mounted—no manual setup required.
---

## 🐳 Getting Started with Docker Compose

All services are managed via Docker Compose. The stack includes the backend, Solr, Postgres, Redis, and the search UI.

### Compose Profiles

| Profile | Command | Description |
|---------|---------|-------------|
| *(none)* | `docker compose up -d` | Start all core services (backend, Solr, Postgres, Redis, UI) |
| `seed` | `docker compose --profile=seed up -d` | Start all services **and** upload the demo JAR for instant data |
| `clear` | `docker compose --profile=clear up clear` | **Wipe all data** from Postgres, Solr, and Redis (keeps services running) |

### Typical workflow

```bash
# First time: start everything and seed demo data
docker compose --profile=seed up -d

# Reset data and re-seed (see Troubleshooting below for details):
docker compose --profile=clear up clear
docker compose down
docker compose --profile=seed up -d
```

### ⚠️ Troubleshooting

#### Solr permission error on first start (self-hosted / VPS)

If Solr fails with `cp: cannot create directory '/var/solr/data/Constants': Permission denied`, the data directory was created by root before Solr's `solr` user could write to it. Fix:

```bash
# On the VPS / host:
sudo rm -rf ./.solr_data
sudo mkdir -p ./.solr_data
sudo chown -R 8983:8983 ./.solr_data   # 8983 is the solr UID inside the image
docker compose up -d solr
```

#### Clear + reseed fails with "network … not found"

After running `docker compose --profile=clear up clear`, the `seed` container retains a reference to the old network. Always do a full `docker compose down` before restarting with the seed profile:

```bash
docker compose --profile=clear up clear   # wipe data
docker compose down                        # tear down network + stopped containers
docker compose --profile=seed up -d       # fresh start with demo data
```

### Rebuilding after code changes

```bash
# Rebuild a specific service image
docker compose build app
docker compose build search-ui

# Restart with the new image
docker compose up -d app
docker compose up -d search-ui
```

### Services & ports

Once running:
- **Search UI**: [http://localhost:5173](http://localhost:5173)
- **API**: [http://localhost:8080](http://localhost:8080)
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Solr UI**: [http://localhost:8983/solr/#/](http://localhost:8983/solr/#/)
- **Postgres**: `localhost:5432`
- **Redis**: `localhost:6379`



---

## 🐳 Docker Compose

All services are managed via Docker Compose. The complete stack includes the backend, Solr, Postgres, Redis, and search UI.

### Profiles

| Profile | Command | Description |
|---------|---------|-------------|
| *(default)* | `docker compose up -d` | Start all core services (backend, Solr, Postgres, Redis, UI) |
| `seed` | `docker compose --profile=seed up -d` | Start services + auto-seed demo data |
| `clear` | `docker compose --profile=clear up clear` | Wipe all data from Postgres, Solr, Redis |

### Common Workflows

**First time setup (with demo data):**
```bash
docker compose --profile=seed up -d
```

**Restart services after code changes:**
```bash
docker compose build app
docker compose build search-ui
docker compose up -d app search-ui
```

**Reset all data:**
```bash
docker compose --profile=clear up clear
docker compose down                        # required — tears down stale network
docker compose --profile=seed up -d       # fresh start with demo data
```

### Service Endpoints (when running)

| Service | URL |
|---------|-----|
| Search UI | [http://localhost:5173](http://localhost:5173) |
| Backend API | [http://localhost:8080](http://localhost:8080) |
| Swagger API Docs | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| Solr Admin | [http://localhost:8983/solr/#/](http://localhost:8983/solr/#/) |
| Postgres | `localhost:5432` |
| Redis | `localhost:6379` |

---

## 🧪 Testing

### Run All Tests

```bash
./gradlew test              # Run all module tests
./gradlew testReport        # Generate combined HTML report
```

### Run Heavy Tests

For large-scale testing (e.g., analyzing full Java 25 runtime):

```bash
./gradlew :constant-tracker-app:heavyTest   # Runs with 16GB heap
```

### Module-Specific Tests

Each core analysis module enforces **≥85% JaCoCo coverage** via the `check` task:

```bash
./gradlew :constant-extractor-bytecode:check    # >90% coverage
./gradlew :constant-extractor-config-file:check  # >85% coverage
```

### View Coverage Reports

After running tests:
- **Combined report:** `build/reports/allTests/index.html`
- **Bytecode module:** `constant-extractor-bytecode/build/jacocoHtml/index.html`
- **Config file module:** `constant-extractor-config-file/build/jacocoHtml/index.html`

### Sample Test Files

Example test files are included in the repository:

**Bytecode samples:**
- `.class` files: `constant-extractor-bytecode/src/test/resources/samples/`
- `.java` source: `constant-extractor-bytecode/src/test/java/org/glodean/constants/samples/`

**App samples:**
- `.class` files: `constant-tracker-app/src/test/resources/samples/`

Example API call:
```bash
curl -X POST "http://localhost:8080/class?project=samples" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @constant-extractor-bytecode/src/test/resources/samples/Greeter.class
```


---

## ✨ Technical Highlights

**Bytecode Analysis:**
- Custom JVM constant pool parser (fields, methods, strings, class references, dynamic invocations)
- Full support for `invokedynamic` and bootstrap method resolution
- Six semantic classifiers: Logging, SQL, URL/Resource, File Path, Error Message, Annotation
- >90% test coverage validating all constant types

**Configuration Analysis:**
- YAML (`.yml`, `.yaml`) extraction via SnakeYAML
- Java properties (`.properties`) extraction
- Unified constant model across all extraction types

**Advanced Features:**
- **Version diffing** — compare two finalized versions and see added/removed/changed constants with full context
- **Project versioning** — open/finalized state, inheritance, removal sync
- Reactive, container-ready architecture (WebFlux + Redis + Solr + Postgres)
- Built on Java 25 (can analyze the full runtime in ~2.5 minutes on i7-13620 with 16GB heap)

---

## 🚀 Build & Deployment

### Build Everything

```bash
./gradlew clean build
```

### Build Libraries Only

```bash
./gradlew :constant-extractor-api:build
./gradlew :constant-extractor-bytecode:build
./gradlew :constant-extractor-config-file:build
```

Libraries are available in each module's `build/libs/` directory.

### Run Locally (Without Docker)

Start external services first (Postgres, Redis, Solr), then:

```bash
./gradlew :constant-tracker-app:bootRun
```

Or run the built JAR:

```bash
java -jar constant-tracker-app/build/libs/constant-tracker-app-0.1.0-SNAPSHOT.jar
```

### API Documentation

**Interactive API docs** (when running):
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 📜 License

MIT © Gabriel Glodean
