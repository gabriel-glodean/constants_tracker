# ---------- Build stage ----------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# 1) Warm Gradle cache (best-effort)
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle ./
COPY build.gradle ./
COPY gradle.properties ./
COPY constant-extractor-lib/build.gradle constant-extractor-lib/
COPY constant-tracker-app/build.gradle constant-tracker-app/
RUN chmod +x gradlew && ./gradlew --no-daemon -q help || true

# 2) Copy sources and build
COPY constant-extractor-lib/src constant-extractor-lib/src
COPY constant-tracker-app/src constant-tracker-app/src
RUN ./gradlew --no-daemon -q :constant-tracker-app:clean :constant-tracker-app:build -x test -x spotlessCheck \
    && ls -l /workspace/constant-tracker-app/build/libs/

# ---------- Runtime stage ----------
FROM eclipse-temurin:25-jre-alpine
ENV APP_HOME=/app \
    JAVA_OPTS="" \
    SERVER_PORT=8080 \
    SOLR_URL=http://solr:8983/solr/ \
    REDIS_HOST=redis \
    REDIS_PORT=6379

WORKDIR $APP_HOME
# Copy the runnable JAR (rename to constant_tracker.jar)
COPY --from=build /workspace/constant-tracker-app/build/libs/constant-tracker-app-0.1.0-SNAPSHOT.jar constant_tracker.jar
RUN ls -l /app

# Non-root user
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p logs \
    && chown app:app logs
USER app

# Expose the internal port (you can change via SERVER_PORT)
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep '"status":"UP"' || exit 1

# Wait for Postgres before starting the app
ENTRYPOINT ["sh","-c","until nc -z postgres 5432; do echo waiting for postgres; sleep 2; done; java $JAVA_OPTS -Dserver.port=${SERVER_PORT} -Dspring.data.redis.host=${REDIS_HOST} -Dspring.data.redis.port=${REDIS_PORT} -Dconstants.solr.url=${SOLR_URL} -jar /app/constant_tracker.jar"]
