# syntax=docker/dockerfile:1

FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /workspace

# 의존성 캐시를 먼저 준비 - 모든 서브모듈의 build.gradle만 먼저 복사해
# 소스 변경이 있어도 이 레이어(의존성 다운로드)는 캐시가 재사용되게 한다.
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties* ./
COPY backend/common/build.gradle backend/common/build.gradle
COPY backend/exception/build.gradle backend/exception/build.gradle
COPY backend/domain/build.gradle backend/domain/build.gradle
COPY backend/application/build.gradle backend/application/build.gradle
COPY backend/infrastructure/build.gradle backend/infrastructure/build.gradle
COPY backend/api/build.gradle backend/api/build.gradle
COPY backend/monitoring/build.gradle backend/monitoring/build.gradle
COPY backend/bootstrap/build.gradle backend/bootstrap/build.gradle
RUN chmod +x gradlew
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon --build-cache :backend:bootstrap:dependencies > /dev/null || true

# 애플리케이션 JAR 빌드
COPY backend/common backend/common
COPY backend/exception backend/exception
COPY backend/domain backend/domain
COPY backend/application backend/application
COPY backend/infrastructure backend/infrastructure
COPY backend/api backend/api
COPY backend/monitoring backend/monitoring
COPY backend/bootstrap backend/bootstrap
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon --build-cache :backend:bootstrap:bootJar -x test && \
    find backend/bootstrap/build/libs/ -name "*.jar" ! -name "*-plain.jar" -exec cp {} backend/bootstrap/build/libs/app.jar \;

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /workspace/backend/bootstrap/build/libs/app.jar /app/app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
