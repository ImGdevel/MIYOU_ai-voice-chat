# syntax=docker/dockerfile:1

FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /workspace

# 의존성 캐시를 먼저 준비
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY app-miyou/build.gradle app-miyou/build.gradle
RUN chmod +x gradlew
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon --build-cache :app-miyou:dependencies > /dev/null || true

# 애플리케이션 JAR 빌드
COPY app-miyou app-miyou
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon --build-cache :app-miyou:bootJar -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /workspace/app-miyou/build/libs/*.jar /app/app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
