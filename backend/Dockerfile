# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true
COPY src ./src
RUN ./gradlew --no-daemon bootJar

# New Relic Java APM agent (Workstream C, 2026-08-12) — downloaded at build time from
# New Relic's own documented, stable endpoint, not committed to the repo. A dedicated
# stage keeps curl/unzip out of the final runtime image entirely.
FROM alpine:3.20 AS newrelic
RUN apk add --no-cache curl unzip
RUN curl -sSfL -o /tmp/newrelic-java.zip https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic-java.zip \
    && unzip -q /tmp/newrelic-java.zip -d /

FROM eclipse-temurin:17-jre-jammy
RUN useradd --system --create-home --shell /usr/sbin/nologin rally26
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
# NEW_RELIC_LICENSE_KEY/NEW_RELIC_APP_NAME are read directly from the container
# environment (docker-compose.prod.yml) and override newrelic.yml automatically — no
# yml editing needed. A blank license key means the agent starts but reports nothing,
# matching every other blank-means-unconfigured provider key in this codebase; it
# never blocks the app from booting.
COPY --from=newrelic --chown=rally26:rally26 /newrelic /app/newrelic
USER rally26
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
