# syntax=docker/dockerfile:1

FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /workspace

# 의존성 캐시를 먼저 준비 - 모든 서브모듈의 build.gradle만 먼저 복사해
# 소스 변경이 있어도 이 레이어(의존성 다운로드)는 캐시가 재사용되게 한다.
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY common/build.gradle common/build.gradle
COPY exception/build.gradle exception/build.gradle
COPY domain/build.gradle domain/build.gradle
COPY application/build.gradle application/build.gradle
COPY infrastructure/build.gradle infrastructure/build.gradle
COPY api/build.gradle api/build.gradle
COPY monitoring/build.gradle monitoring/build.gradle
COPY bootstrap/build.gradle bootstrap/build.gradle
RUN chmod +x gradlew
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon --build-cache :bootstrap:dependencies > /dev/null || true

# 애플리케이션 JAR 빌드
COPY common common
COPY exception exception
COPY domain domain
COPY application application
COPY infrastructure infrastructure
COPY api api
COPY monitoring monitoring
COPY bootstrap bootstrap
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon --build-cache :bootstrap:bootJar -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /workspace/bootstrap/build/libs/*.jar /app/app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
