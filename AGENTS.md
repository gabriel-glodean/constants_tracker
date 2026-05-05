# AGENTS.md — Constant Tracker

## Architecture

Four-module Gradle project.

| Module | Role |
|--------|------|
| `constant-extractor-api` | Shared model + SPI — no framework dependencies |
| `constant-extractor-bytecode` | JVM bytecode parser + semantic classifiers |
| `constant-extractor-config-file` | YAML / `.properties` extraction |
| `constant-tracker-app` | Spring Boot 3 / WebFlux service, REST API, persistence |

### Extraction request flow

```
POST /class  (ClassBinariesController)  ← requires Bearer token when auth is enabled
  → ConcreteExtractionService            (creates ModelExtractor)
    → ClassModelExtractor / FileSystemModelExtractor  (constant-extractor-bytecode)
      → ByteCodeMethodAnalyzer + AnalysisMerger + InstructionHandlers
        → ClassConstants (model)
  → SolrService.store()                  (parent doc + child docs per constant usage)
  → Redis                                (Spring Cache + atomic version counter)
```

### Auth request flow

```
POST /auth/login
  → JwtService.authenticate()            (BCrypt verify via AuthUserService)
    → AuthRefreshTokenService.issue()    (revokes old tokens, saves new one)
    → returns TokenResponse { accessToken, refreshToken }

POST /auth/refresh  (Bearer not required)
  → JwtService.refresh()
    → AuthRefreshTokenService.validate() + issue()  (refresh-token rotation)

POST /auth/logout
  → JwtService.logout()
    → Mono.when(blacklist access token, revoke refresh token)  (parallel)

Scheduled (AuthCleanupScheduler)
  → AuthTokenBlacklistService.purgeExpired()   (cron: constants.auth.cleanup.blacklist-cron)
  → AuthRefreshTokenService.purgeExpired()     (cron: constants.auth.cleanup.refresh-tokens-cron)
```

**Key packages in `constant-extractor-bytecode`:**
- `org.glodean.constants.extractor.bytecode` — `ClassModelExtractor`, `ByteCodeMethodAnalyzer`, `AnalysisMerger`, per-opcode `handlers/impl/*`
- `org.glodean.constants.interpreter` — `ConstantUsageInterpreter` strategy interface + context types (`MethodCallContext`, `FieldStoreContext`, etc.)
- `org.glodean.constants.extractor.bytecode.interpreters` — concrete classifiers (e.g., `LoggingConstantUsageInterpreter`)
- `org.glodean.constants.model` — `ClassConstants`, `ClassConstant`, `UsageType` (structural), `CoreSemanticType` (semantic), `ConstantUsage`, `UsageLocation`

**Key packages in `constant-tracker-app`:**
- `org.glodean.constants.auth` — `JwtService`, `SecurityConfiguration`, `AuthCleanupScheduler`, `AuthProperties`, `AuthRefreshTokenService`, `AuthTokenBlacklistService`, `AuthUserService`
- `org.glodean.constants.web.endpoints` — `LoginController`, `ClassBinariesController`, `JarBinariesController`, `FuzzySearchController`, `DiffController`, `VersionController`
- `org.glodean.constants.web` — `GlobalExceptionHandler`, `WebCorsConfiguration`
- `org.glodean.constants.services` — `ExtractionServiceConfiguration` (wires `AnalysisMerger` + `ConstantUsageInterpreterRegistry`)
- `org.glodean.constants.store.solr` — `SolrService` (Solr parent/child document model, URL via `constants.solr.url`)
- `org.glodean.constants.store.redis` — `RedisAtomicIntegerBasedVersionIncrementer`
- `org.glodean.constants.store.postgres` — R2DBC repositories for constants, versions, diffs, auth tokens

## Build & Test Commands

```bash
./gradlew test                                  # run all tests (triggers JaCoCo)
./gradlew testReport                            # combined HTML report → build/reports/allTests/
./gradlew :constant-tracker-app:heavyTest       # 16 GB heap; matches org.glodean.constants.heavy.*
./gradlew spotlessApply                         # auto-fix formatting (run before committing)
./gradlew spotlessApplyAll                      # formatting across all subprojects
./gradlew :constant-extractor-bytecode:check    # tests + JaCoCo coverage gate (≥ 85%)
```

`check` on `constant-extractor-bytecode` enforces **85% minimum** JaCoCo coverage. Coverage report: `constant-extractor-bytecode/build/jacocoHtml/index.html`.

### UI

```bash
cd search-ui
npm install
npm test -- --watchAll=false   # all Jest tests (212 total)
npm run dev                    # Vite dev server → http://localhost:5173
npm run build                  # production build
```

## Conventions & Patterns

- **Java records everywhere**: models (`ClassConstants`, `ClassConstant`), DTOs (`GetUnitConstantsReply`), extractors (`ClassModelExtractor`), service beans (`ConcreteExtractionService`).
- **Sealed `SemanticType`**: `CoreSemanticType` (enum) and `CustomSemanticType` (record) both implement it. Add new built-in types to `CoreSemanticType`; add plugin types via `CustomSemanticType`.
- **Adding a semantic classifier**: implement `ConstantUsageInterpreter`, then register it in `ExtractionServiceConfiguration.interpreterRegistry()` via `ConstantUsageInterpreterRegistry.builder().register(UsageType.X, new MyInterpreter())`. Only `METHOD_INVOCATION_PARAMETER` has a default interpreter (`LoggingConstantUsageInterpreter`).
- **Logging**: Log4j2 only — Logback is excluded in every Gradle configuration block. Use `LogManager.getLogger(MyClass.class)`.
- **Reactive layer**: all store / controller methods return `Mono<>`. The extractor lib is synchronous; wrapping happens at the service boundary. Use `Mono.when()` for parallel independent reactive operations.
- **JAR handling**: uses Jimfs (in-memory filesystem) — see `ConcreteExtractionService.fromZipBytesWithJimfs()`.
- **Bean validation**: `@Valid` on controller `@RequestBody` parameters; `@NotBlank` / `@NotNull` on DTO record components. `WebExchangeBindException` is handled in `GlobalExceptionHandler` and returns HTTP 400 with field-level details.
- **Auth gating**: `JwtService`, `LoginController`, and `AuthCleanupScheduler` are all annotated `@ConditionalOnProperty(name = "constants.auth.enabled", havingValue = "true")`. Set `CONSTANTS_AUTH_ENABLED=false` to run without auth.
- **Refresh token rotation**: every call to `AuthRefreshTokenService.issue()` (login, refresh, renew) revokes all existing tokens for the user before saving the new one. The UI must always persist the `refreshToken` returned by `/auth/refresh` — it is never the same token that was sent.
- **Security headers**: `SecurityConfiguration` adds `X-Frame-Options: DENY` and `Referrer-Policy: strict-origin-when-cross-origin`. HSTS is deliberately disabled — TLS is terminated by the Cloudflare tunnel upstream.
- **CORS**: configured in `WebCorsConfiguration`; allowed origins read from `constants.cors.allowed-origins` (env: `CONSTANTS_CORS_ALLOWED_ORIGINS`). Credentials are allowed; the methods list includes `DELETE`.

## Integration Tests

Integration tests (`constant-tracker-app/src/test/.../integration/`) use Testcontainers. Redis is auto-configured via `@ServiceConnection`; Solr is wired with `@DynamicPropertySource` and mounts the configset from `constant-tracker-app/solr/`. Sample `.class` files for tests live in `constant-extractor-bytecode/src/test/resources/samples/` and `constant-tracker-app/src/test/resources/samples/`.

## External Services (local dev)

- **Solr 9**: `http://localhost:8983/solr/` — collection `Constants`, schema in `constant-tracker-app/solr/managed-schema.xml`
- **Redis 7**: `localhost:6379`
- **PostgreSQL 17**: `localhost:5432` — database `constant_tracker`; schema managed by Flyway (migrations in `constant-tracker-app/src/main/resources/db/migration/`)
- Spin up all three: `docker compose up -d` (from repo root)
