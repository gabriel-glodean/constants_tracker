# Constant Tracker

A Spring Boot (WebFlux) service that indexes **Java bytecode constants** for fast search and analysis using Solr.

This started as an experiment in exploring the JVM class file structure — parsing the constant pool, resolving references, and indexing them into Solr for querying and visualization.  
The focus of this project is **bytecode analysis correctness**, but in the future analysis of java files might be on the table.

---

## 🧠 Design Focus

The central module implements a **JVM ClassFile parser** (compatible with class file format version 69 / JDK 25) and constant-usage extractor.  
It’s tested at over **90 % coverage**, validating every supported constant type including `invokedynamic`, method handles, and bootstrap methods.

WebFlux and Redis layers are intentionally minimal; they serve as integration and caching shells for the core analysis engine.

---

## 🧩 Architecture

```
[ .class upload ]
       │
       ▼
[ Reactive controller ]
       │
       ▼
[ Analysis engine ]
       │
       ▼
[ Redis cache ] → [ Solr index ]
```

- **Spring Boot 3 / WebFlux** – reactive REST interface  
- **Solr 9** – full-text indexing of constant references  
- **Redis 7** – caching
- **Java 25** – uses latest features including ClassFile API  

---

## 🚀 Quick Start

```bash
# Build the image
docker build -t constant_tracker:latest .

# Option A: Docker Compose (not yet there)
docker compose up -d

# Option B: Terraform (advanced)
terraform init && terraform apply -auto-approve

# Upload a class file for analysis
curl -X POST "http://localhost:8080/class?project=demo"   -H "Content-Type: application/octet-stream"   --data-binary @samples/Greeter.class
```

Once started:
- API → http://localhost:8080  
- Solr UI → http://localhost:8983/solr/#/  
- Swagger UI → http://localhost:8080/swagger-ui.html

---

## 🧪 Tests

- **Bytecode parser:** > 80+ % coverage (JaCoCo report under `build/reports/jacoco`)  
- **Reactive API:** minimal tests verifying upload and integration  
- **Integration stack:** Terraform / Docker Compose for Solr + Redis environments  

---

## 📦 Mock Files and Samples

Example `.class` files are included in the [`samples/`](./samples) directory.  
They can be used to test or demonstrate the analysis process without compiling your own Java sources.

```bash
# Example: analyze a provided mock class
curl -X POST "http://localhost:8080/class?project=samples"   -H "Content-Type: application/octet-stream"   --data-binary @samples/ExampleConstants.class
```

These mock classes cover:
- simple constant pools
- field and method reference examples  

---

## 📸 Screenshot

![Application screenshot](PLACEHOLDER_FOR_YOUR_SCREENSHOT_URL)

_Add your screenshot link above once uploaded — e.g. `docs/screenshot.png` or a GitHub asset._

---

## 🧩 Technical Highlights

- Custom parser for JVM constant pool (fields, methods, strings, class refs, dynamic invocations)  
- Handles `invokedynamic` and bootstrap method resolution  
- Exports constants and metadata to Solr documents  
- Reactive and container-ready (WebFlux + Redis + Solr)  
- Built and tested on JDK 25  

---

## 🛠️ Build & Run Locally

```bash
./gradlew clean build
java -jar build/libs/constant-tracker-*.jar
```

---

## 🧭 Future Work

- Enrich analysis with method flow graphs  
- Add Testcontainers integration tests  
- Extend Solr schema for cross-reference search  
- Expose OpenAPI / Swagger documentation  
- Optional GraalVM native image  

---

## 📜 License

MIT © Gabriel Glodean
