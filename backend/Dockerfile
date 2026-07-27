# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre-jammy
RUN useradd --system --create-home --shell /usr/sbin/nologin leaguelift
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER leaguelift
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
