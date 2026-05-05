# ops-tool

A self-contained Python container that replaces the inline shell scripts embedded
in `docker-compose.yml`, `k8s/jobs.yml`, and `main.tf`.

## Commands

| `CMD` value      | What it does                                                               |
|------------------|----------------------------------------------------------------------------|
| `seed`           | Uploads demo JARs (v1 + v2) and calls sync/finalize for each version      |
| `clear`          | Truncates all app tables and wipes Solr + Redis entirely                   |
| `clear-demo`     | Deletes all data for `DEMO_PROJECT` from PostgreSQL, Solr, and Redis       |
| `seed-auth-user` | Upserts the demo user into `auth_users` with a BCrypt-hashed password      |

## Environment variables

### Always required

| Variable  | Description                  |
|-----------|------------------------------|
| `CMD`     | One of the values in the table above |

### PostgreSQL (required for `clear`, `clear-demo`, `seed-auth-user`)

| Variable            | Default              |
|---------------------|----------------------|
| `POSTGRES_HOST`     | `postgres`           |
| `POSTGRES_PORT`     | `5432`               |
| `POSTGRES_USER`     | *(required)*         |
| `POSTGRES_PASSWORD` | *(required)*         |
| `POSTGRES_DB`       | `constant_tracker`   |

### Solr (required for `clear`, `clear-demo`)

| Variable               | Default                      |
|------------------------|------------------------------|
| `SOLR_URL`             | `http://solr:8983/solr/`     |
| `SOLR_CORE`            | `Constants`                  |
| `SOLR_PING_TIMEOUT_S`  | `120`                        |

### Redis (required for `clear`, `clear-demo`)

| Variable      | Default  |
|---------------|----------|
| `REDIS_HOST`  | `redis`  |
| `REDIS_PORT`  | `6379`   |

### App / Seed (required for `seed`)

| Variable      | Default               |
|---------------|-----------------------|
| `APP_URL`     | `http://app:8080`     |
| `PROJECT`     | `demo-crud-server`    |
| `JAR_DIR_V1`  | `/seed/jars/v1`       |
| `JAR_DIR_V2`  | `/seed/jars/v2`       |

### Auth (required when `AUTH_ENABLED=true` or `CMD=seed-auth-user`)

| Variable        | Default |
|-----------------|---------|
| `AUTH_ENABLED`  | `false` |
| `DEMO_USERNAME` | —       |
| `DEMO_PASSWORD` | —       |

### Clear-demo

| Variable       | Default           |
|----------------|-------------------|
| `DEMO_PROJECT` | `demo-crud-server`|

## Build & run locally

```bash
# build
docker build -t ops-tool ops-tool/

# clear everything
docker run --rm --network app_network \
  -e CMD=clear \
  -e POSTGRES_USER=myuser \
  -e POSTGRES_PASSWORD=mypass \
  ops-tool

# seed demo data (no auth)
docker run --rm --network app_network \
  -v ./demo-crud-server/build/libs:/seed/jars/v1:ro \
  -v ./demo-crud-server-v2/build/libs:/seed/jars/v2:ro \
  -e CMD=seed \
  ops-tool
```

## Design notes

- Config is validated at startup by **Pydantic Settings v2** — the container exits with a clear error message if a required variable is missing.
- External API responses (Solr, app auth) are validated with **Pydantic BaseModel** to catch unexpected payloads early.
- **Fully async** — every command is a coroutine driven by `asyncio.run()`. Network I/O uses `aiohttp` (HTTP) and `redis.asyncio` (Redis); database access uses `asyncpg`.
- `seed-auth-user` hashes passwords with Python's `bcrypt` library (cost 12, `$2b$` prefix) offloaded to a thread-pool executor so the event loop is never blocked. Spring Security's `BCryptPasswordEncoder` accepts both `$2a$` and `$2b$`, so no `pgcrypto` extension is required.
- `wait_for_solr` polls the core ping endpoint with `asyncio.sleep` between attempts before any write, so the container can start alongside Solr without `depends_on` health checks.

