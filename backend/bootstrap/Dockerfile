# syntax=docker/dockerfile:1

FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /workspace

# 의존성 캐시를 먼저 준비 - 모든 서브모듈의 build.gradle만 먼저 복사해
# 소스 변경이 있어도 이 레이어(의존성 다운로드)는 캐시가 재사용되게 한다.
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties* ./
COPY backend/build.gradle backend/build.gradle
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
    find backend/bootstrap/build/libs/ -name "*.jar" ! -name "*-plain.jar" ! -name "app.jar" -exec cp {} backend/bootstrap/build/libs/app.jar \;

# CDS(Class Data Sharing) 트레이닝 - ApplicationContext refresh 직후 바로
# 종료해 동적 CDS 아카이브를 만든다. QdrantCollectionInitializer(유일한
# ApplicationRunner)는 refresh 이후에 실행되므로 이 시점엔 아직 안 돎 -
# Mongo/Redis/Qdrant가 없는 빌드 컨테이너에서도 안전하게 트레이닝된다.
# 트레이닝이 실패하면(환경 문제 등) 빌드 자체를 실패시켜 조용히 CDS 없이
# 배포되는 상황을 막는다.
RUN java -Dspring.context.exit=onRefresh \
    -XX:ArchiveClassesAtExit=backend/bootstrap/build/libs/app.jsa \
    -jar backend/bootstrap/build/libs/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /workspace/backend/bootstrap/build/libs/app.jar /app/app.jar
COPY --from=builder /workspace/backend/bootstrap/build/libs/app.jsa /app/app.jsa

EXPOSE 8081
# app.jsa가 없거나(빌드 이슈) 손상됐어도 JVM이 CDS를 조용히 비활성화하고
# 정상 기동하지만, 파일 존재 여부까지 셸에서 한 번 더 확인해 이중으로 안전하게 처리한다.
ENTRYPOINT ["/bin/sh", "-c", "if [ -f /app/app.jsa ]; then exec java -XX:SharedArchiveFile=/app/app.jsa -jar /app/app.jar; else exec java -jar /app/app.jar; fi"]
