# ── Stage 1: compile native image ─────────────────────────────────────────────
# GraalVM community edition for JDK 25, includes the native-image tool.
FROM ghcr.io/graalvm/native-image-community:25 AS builder

WORKDIR /workspace

# Gradle wrapper is shipped from the repo root (ops-tool has no wrapper of its own).
# These layers are cached until the wrapper version changes.
COPY gradle/ gradle/
COPY gradlew gradlew.bat ./

# ops-tool is a standalone Gradle project — copy its build files and sources.
COPY ops-tool/settings.gradle ops-tool/build.gradle ./
COPY ops-tool/src/ src/

RUN chmod +x gradlew && \
    ./gradlew nativeCompile --no-daemon -q

# ── Stage 2: minimal runtime ──────────────────────────────────────────────────
# debian:12-slim includes glibc + zlib + other libs linked by the GraalVM binary.
# Distroless static/base images lack libz.so.1 which the OL9-built native binary needs.
FROM debian:12-slim

COPY --from=builder /workspace/build/native/nativeCompile/ops-tool /ops-tool

ENTRYPOINT ["/ops-tool"]
