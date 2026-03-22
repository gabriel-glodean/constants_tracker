# AGENTS.md — Constant Tracker

## Architecture

Two-module Gradle project. **`constant-extractor-lib`** is a pure Java 25 library (no Spring); **`constant-tracker-app`** is a Spring Boot 3 / WebFlux service that wraps it.

```
POST /class (ClassBinariesController)
  → ConcreteExtractionService          (creates ModelExtractor)
    → ClassModelExtractor / FileSystemModelExtractor (constant-extractor-lib)
      → ByteCodeMethodAnalyzer + AnalysisMerger + InstructionHandlers
        → ClassConstants (model)
  → SolrService.store()                (parent doc + child docs per constant usage)
  → Redis                              (Spring Cache + atomic version counter)
```

**Key packages in `constant-extractor-lib`:**
- `org.glodean.constants.extractor.bytecode` — `ClassModelExtractor`, `ByteCodeMethodAnalyzer`, `AnalysisMerger`, per-opcode `handlers/impl/*`
- `org.glodean.constants.interpreter` — `ConstantUsageInterpreter` strategy interface + context types (`MethodCallContext`, `FieldStoreContext`, etc.)
- `org.glodean.constants.extractor.bytecode.interpreters` — concrete classifiers (e.g., `LoggingConstantUsageInterpreter`)
- `org.glodean.constants.model` — `ClassConstants`, `ClassConstant`, `UsageType` (structural), `CoreSemanticType` (semantic), `ConstantUsage`, `UsageLocation`

**Key packages in `constant-tracker-app`:**
- `org.glodean.constants.services` — `ExtractionServiceConfiguration` (Spring `@Configuration` that wires `AnalysisMerger` + `ConstantUsageInterpreterRegistry`)
- `org.glodean.constants.store.solr` — `SolrService` (Solr parent/child document model, URL via `constants.solr.url`)
- `org.glodean.constants.store.redis` — `RedisAtomicIntegerBasedVersionIncrementer`

## Build & Test Commands

```bash
./gradlew test                                 # run all tests (triggers JaCoCo)
./gradlew testReport                           # combined HTML report → build/reports/allTests/
./gradlew :constant-tracker-app:heavyTest      # 16 GB heap; matches org.glodean.constants.heavy.*
./gradlew spotlessApply                        # auto-fix formatting (run before committing)
./gradlew spotlessApplyAll                     # formatting across all subprojects
./gradlew :constant-extractor-lib:check        # tests + JaCoCo coverage gate (≥ 85%)
```

`check` on both modules enforces **85% minimum** JaCoCo coverage. Coverage reports: `{module}/build/jacocoHtml/index.html`.

## Conventions & Patterns

- **Java records everywhere**: models (`ClassConstants`, `ClassConstant`), DTOs (`GetClassConstantsReply`), extractors (`ClassModelExtractor`), service beans (`ConcreteExtractionService`).
- **Sealed `SemanticType`**: `CoreSemanticType` (enum) and `CustomSemanticType` (record) both implement it. Add new built-in types to `CoreSemanticType`; add plugin types via `CustomSemanticType`.
- **Adding a semantic classifier**: implement `ConstantUsageInterpreter`, then register it in `ExtractionServiceConfiguration.interpreterRegistry()` via `ConstantUsageInterpreterRegistry.builder().register(UsageType.X, new MyInterpreter())`. Only `METHOD_INVOCATION_PARAMETER` has a default interpreter (`LoggingConstantUsageInterpreter`).
- **Logging**: Log4j2 only — Logback is excluded in every Gradle configuration block. Use `LogManager.getLogger(MyClass.class)`.
- **Reactive layer**: all `ClassConstantsStore` / controller methods return `Mono<>`. The lib is synchronous; wrapping happens at the service boundary.
- **JAR handling**: uses Jimfs (in-memory filesystem) — see `ConcreteExtractionService.fromZipBytesWithJimfs()`.

## Integration Tests

Integration tests (`constant-tracker-app/src/test/.../integration/`) use Testcontainers. Redis is auto-configured via `@ServiceConnection`; Solr is wired with `@DynamicPropertySource` and mounts the configset from `constant-tracker-app/solr/`. Sample `.class` files for tests live in `constant-extractor-lib/src/test/resources/samples/` and `constant-tracker-app/src/test/resources/samples/`.

## External Services (local dev)

- **Solr 9**: `http://localhost:8983/solr/` — collection `Constants`, schema in `constant-tracker-app/solr/managed-schema.xml`
- **Redis 7**: `localhost:6379`
- Spin up both: `docker compose -f constant-tracker-app/docker-compose.yml up -d`

