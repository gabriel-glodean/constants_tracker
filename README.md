# Constant Tracker

A Spring Boot (WebFlux) service that indexes **Java bytecode constants** for fast search and analysis using Solr.

This started as an experiment in exploring the JVM class file structure — parsing the constant pool, resolving
references, and indexing them into Solr for querying and visualization.  
The focus of this project is **bytecode analysis correctness**, but in the future analysis of java files might be on the
table.

---

## 🧠 Design Focus

This project is split into **three modules** for clean separation and reusability:

- The **`constant-extractor-lib`** module implements a **JVM ClassFile parser** (compatible with class file format version 69 / JDK 25) and constant-usage extractor. It's tested at over **90% coverage**, validating every supported constant type including `invokedynamic`, method handles, and bootstrap methods.
- The **`constant-tracker-app`** module provides a reactive web service built with WebFlux. The Redis and Solr layers are intentionally minimal; they serve as integration and caching shells for the core analysis engine.
- The **`search-ui`** module is a lightweight web UI for searching and browsing indexed constants. It is served as a static site and communicates with the backend API.

The library can be used **standalone** in any Java project that needs bytecode analysis capabilities.

---

## 🧩 Architecture

This is a **multi-module Gradle project** with clear separation of concerns. In addition to the backend modules, the project includes a small search UI for browsing and querying indexed constants:

### Modules

1. **`constant-extractor-lib`** – Core bytecode analysis library
   - JVM ClassFile parser (format version 69 / JDK 25)
   - Constant pool extraction and resolution
   - Model classes for bytecode structures
   - Zero external dependencies (except Guava and testing tools)
   - Can be used standalone in any Java project

2. **`constant-tracker-app`** – Spring Boot application
   - Reactive REST API (WebFlux)
   - Redis caching layer + versioning
   - Solr indexing integration
   - Database persistence with Postgres
   - Docker and Terraform deployment
   - Depends on `constant-extractor-lib`

3. **`search-ui`** – Web-based search interface
   - Lightweight static web UI for searching and browsing indexed constants
   - Communicates with the backend API
   - Located in the `search-ui/` directory (served as a static site)

### Data Flow

**Class/JAR Upload and Indexing:**
```
[ .class/.jar upload ]
       │
       ▼
[ WebFlux Controller ] (constant-tracker-app)
       │
       ▼
[ Analysis Engine ] (constant-extractor-lib)
       │
       ▼
[ Redis Cache ] → [ Solr Index + Postgres DB ] (constant-tracker-app)
```

**Class and Project Query:**
```
[ class and project query ]
       │
       ▼
[ WebFlux Controller ] (constant-tracker-app)
       │
       ▼
[ Redis Cache ] → [ Postgres DB ] (constant-tracker-app)
```

**Fuzzy Search Constant Query:**
```
[ fuzzy search constant query ]
       │
       ▼
[ WebFlux Controller ] (constant-tracker-app)
       │
       ▼
[ Redis Cache ] → [ Solr Index ] (constant-tracker-app)
```

### Technology Stack

**Backend:**
- Spring Boot 3 / WebFlux – reactive REST interface
- Solr 10 – full-text indexing of constant references
- Postgres 18 – relational storage for detailed class constants usage
- Redis 7 – caching
- Java 25 – uses latest features including ClassFile API

**Frontend (search-ui):**
- React 19 + TypeScript – UI framework and language
- Vite 8 – build tool and dev server
- Tailwind CSS v4 – utility-first CSS framework
- Lucide React – icon library
- Native fetch API – for backend communication
- ESLint – code linting
- Nginx – static file serving in production Docker
- Docker – containerization for deployment

---

## 🖥️ UI

This project includes a simple search UI for browsing and querying indexed constants.

- **Location:** `search-ui/` directory (served as a static site)
- **Access:**
  - When running with Docker Compose, the UI is available at: [http://localhost:5173](http://localhost:5173)
  - The UI communicates with the backend API at [http://localhost:8080](http://localhost:8080)

---

## 🗄️ Database


The application uses three main data stores:

- **Solr 9/10**: Full-text search and indexing of constant references.
  - Default URL: [http://localhost:8983/solr/](http://localhost:8983/solr/)
  - Collection: `Constants`
  - Schema: `constant-tracker-app/solr/managed-schema.xml`
  - ⚠️ Before starting Solr, copy the schema file to the Solr data folder (see below).
- **Postgres**: Relational database for persistent storage of metadata and application state.
  - Default URL: `jdbc:postgresql://localhost:5432/constants`
  -  ⚠️ Before starting  Postgres, ensure the database credentials are specified in the environment variables.
- **Redis 7**: Caching and versioning.
  - Default URL: `localhost:6379`

All services are started automatically with Docker Compose for local development.

**Solr schema setup:**
Copy the schema file before starting Solr:

**Solr core setup (REQUIRED):**

Before starting Solr, copy the following files to your Solr core's config directory. This step is required for correct indexing and search functionality:

- `constant-tracker-app/solr/managed-schema.xml` → `<solr_core_dir>/conf/managed-schema.xml`
- `constant-tracker-app/solr/solrconfig.xml` → `<solr_core_dir>/conf/solrconfig.xml`
- `constant-tracker-app/solr/core.properties` → `<solr_core_dir>/core.properties`

Replace `<solr_core_dir>` with your Solr core directory (for example, `constant-tracker-app/solr/data/Constants`).

**PowerShell (Windows):**
```powershell
Copy-Item constant-tracker-app/solr/managed-schema.xml <solr_core_dir>/conf/managed-schema.xml -Force
Copy-Item constant-tracker-app/solr/solrconfig.xml <solr_core_dir>/conf/solrconfig.xml -Force
Copy-Item constant-tracker-app/solr/core.properties <solr_core_dir>/core.properties -Force
```

**Bash (Linux/macOS):**
```bash
cp constant-tracker-app/solr/managed-schema.xml <solr_core_dir>/conf/managed-schema.xml
cp constant-tracker-app/solr/solrconfig.xml <solr_core_dir>/conf/solrconfig.xml
cp constant-tracker-app/solr/core.properties <solr_core_dir>/core.properties
```
Replace `<solr_core_dir>` with your Solr core directory (e.g., `constant-tracker-app/solr/data/Constants/conf` for config files and `constant-tracker-app/solr/data/Constants` for core.properties).


---

## 🐳 Getting Started with Docker Compose

To launch the full stack (backend, Solr, Postgres, Redis, and UI) locally, you must first build both the backend and frontend (UI) Docker images:

**1. Build the backend container:**
```bash
docker build -f constant-tracker-app/Dockerfile -t constant_tracker:latest .
```

**2. Build the frontend (UI) container:**
```bash
docker build -f search-ui/Dockerfile -t search_ui:latest ./search-ui
```

**3. Start all services with Docker Compose:**
```bash
docker compose -f constant-tracker-app/docker-compose.yml up -d
```

This will start:
- **API**: [http://localhost:8080](http://localhost:8080)
- **Solr UI**: [http://localhost:8983/solr/#/](http://localhost:8983/solr/#/)
- **Postgres**: `localhost:5432`
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Search UI**: [http://localhost:5173](http://localhost:5173)
- **Redis**: `localhost:6379` (no web UI)

You can now upload `.class` or `.jar` files and use the UI to search indexed constants.



## 🧪 Tests

### Run All Tests
```bash
./gradlew test              # Run tests in all modules
./gradlew testAll           # Alternative: run all tests and show summary
./gradlew testReport        # Generate combined test report
```

### Run Heavy Tests (JRT Filesystem Analysis)
```bash
./gradlew :constant-tracker-app:heavyTest   # Run with 16GB heap
```

### Module-Specific Tests
- **`constant-extractor-lib`:** > 85% coverage (JaCoCo report under `constant-extractor-lib/build/jacocoHtml`)
- **`constant-tracker-app`:** Integration tests verifying upload, caching, and Solr indexing

### Test Reports
- Combined report: `build/reports/allTests/index.html`
- Per-module JaCoCo: `{module}/build/jacocoHtml/index.html`

---

## 📦 Mock Files and Samples

Example `.class` and `.java` files are included in the test resources:
- `.class` files: `constant-extractor-lib/src/test/resources/samples/` and `constant-tracker-app/src/test/resources/samples/`
- `.java` files: `constant-extractor-lib/src/test/java/org/glodean/constants/samples/`

They can be used to test or demonstrate the analysis process without compiling your own Java sources.

```bash
# Example: analyze a provided mock class
curl -X POST "http://localhost:8080/class?project=samples" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @constant-extractor-lib/src/test/resources/samples/Greeter.class
```

---

## 📸 Screenshot
In Postman you can store the Greeter.class file as follows:
![Application screenshot](./docs/postman-post.jpg)
You can check if it was store using Postman:
![Application screenshot](./docs/postman-get.jpg)
or with the Solr UI:
![Application screenshot](./docs/solr.jpg)

The UI layer looks like this:
![Application screenshot](./docs/search-ui.png)

---

## 🧩 Technical Highlights

- Custom parser for JVM constant pool (fields, methods, strings, class refs, dynamic invocations)
- Handles `invokedynamic` and bootstrap method resolution
- Exports constants and metadata to Solr documents
- Reactive and container-ready (WebFlux + Redis + Solr)
- Built and tested on JDK 25 (it can analyze all the java 25 runtime in about 2 and a half minutes on an i7-13620 with 16 GB RAM allocated to the JVM)

---

## 📚 API Documentation

- Swagger UI: /swagger-ui.html or /swagger-ui/index.html
- OpenAPI JSON v3/api-docs

## 🛠️ Build & Run Locally

### Build All Modules
```bash
./gradlew clean build
```

### Run the Application
```bash
# From the root directory
./gradlew :constant-tracker-app:bootRun

# Or run the built JAR
java -jar constant-tracker-app/build/libs/constant-tracker-app-0.1.0-SNAPSHOT.jar
```

### Build Only the Library
```bash
./gradlew :constant-extractor-lib:build
```

The library JAR will be available at `constant-extractor-lib/build/libs/`

---

## 📜 License

MIT © Gabriel Glodean
