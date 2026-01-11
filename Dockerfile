# syntax=docker/dockerfile:1

FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /workspace

# Prime dependency cache first
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY config config
COPY webflux-dialogue/build.gradle webflux-dialogue/build.gradle
RUN chmod +x gradlew
RUN ./gradlew --no-daemon :webflux-dialogue:dependencies > /dev/null || true

# Build app jar
COPY webflux-dialogue webflux-dialogue
RUN ./gradlew --no-daemon :webflux-dialogue:bootJar -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /workspace/webflux-dialogue/build/libs/*.jar /app/app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
