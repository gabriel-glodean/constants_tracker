# ---------- Build stage ----------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# 1) Warm Gradle cache (best-effort)
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle* ./
COPY build.gradle* ./
COPY gradle.properties* ./
RUN chmod +x gradlew && ./gradlew --no-daemon -q help || true

# 2) Copy sources and build
COPY src src
RUN ./gradlew --no-daemon -q clean build -x test

# ---------- Runtime stage ----------
FROM eclipse-temurin:25-jre-alpine
ENV APP_HOME=/app \
    JAVA_OPTS="" \
    SERVER_PORT=8080 \
    SOLR_URL=http://solr:8983/solr/ \
    REDIS_HOST=redis \
    REDIS_PORT=6379
WORKDIR $APP_HOME

# Copy the runnable JAR
COPY --from=build /workspace/build/libs/*.jar constant_tracker.jar

# Non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

# Expose the internal port (you can change via SERVER_PORT)
EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Dserver.port=${SERVER_PORT} -Dspring.data.redis.host=${REDIS_HOST} -Dspring.data.redis.port=${REDIS_PORT} -Dconstants.solr.url=${SOLR_URL} -jar /app/constant_tracker.jar"]